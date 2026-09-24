/*
  Wave 5 配送打印追踪 E2E：自建线路 → 挂单 → 补坐标 → 规划，再验证双只读视角、GET 预览不计次、
  正式登记计次与幂等、按客户 PARTIAL 只补未打印订单，以及缺权限账号被服务端拒。

  原先这组用例靠外部注入的一次性管理员令牌门控（缺令牌即整组 skip），于是「文件存在」被当成了
  「场景已验收」；现在它自建临时账号与线路，收尾取消线路把订单退回候选池并删除账号。
  口径按 §1.2 钉死：打印只登记计次，绝不等于发货确认，也不扣减库存。
*/
import {type APIRequestContext} from '@playwright/test';
import {expect, test} from './scm-test-base';
import {randomUUID} from 'node:crypto';
import {accessibleName, apiClient, authenticate, login, provisionTempAccounts, type TempAccounts} from './scm-e2e-account';
import {createDeliveryReadyOrder, createLocatedCustomer, createSku} from './scm-delivery-fixtures';

type Row = Record<string, any>;

const accounts: TempAccounts = provisionTempAccounts('w5');
const routeName = accounts.admin + '配送线路';
/** 本轮夹具编码前缀：临时账号名本身就是逐次唯一的，拿它当标识避免与上一轮的商品/客户撞码。 */
const routeTag = accounts.admin.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
/** 停靠点坐标与仓库起点同坐标系（GCJ02）即可规划，取值只服务几何展示。 */
const STOP_COORDS = [{lng: '113.22000000', lat: '23.28000000'}, {lng: '113.23000000', lat: '23.29000000'}];

let api: APIRequestContext;
let adminToken: string;
let readOnlyToken: string;
let routeId = '';
/** 线路上挂的三张订单：同一客户两张（构成 PARTIAL）+ 另一客户一张（证明筛选不外溢）。 */
let picked: Row[] = [];
let primaryCustomerId = '';

test.describe.configure({mode: 'serial'});

async function ok<T = Row>(client: APIRequestContext, method: 'get' | 'post' | 'put', path: string, data?: unknown, key = randomUUID()): Promise<T> {
  const response = method === 'get'
      ? await client.get(path)
      // 计次类命令在后端强制要求 Idempotency-Key（缺失即 40069），写命令统一带键
      : await client[method](path, {data, headers: {'Idempotency-Key': key}});
  const body = await response.json();
  expect(body.code, `${method.toUpperCase()} ${path}: ${body.msg}`).toBe(0);
  return body.data as T;
}
const get = <T = Row>(path: string, client: APIRequestContext = api) => ok<T>(client, 'get', path);
/** 只取响应信封，用于反例断言（不预设成功）。 */
async function envelope(client: APIRequestContext, path: string, data: Row, key = randomUUID()) {
  return (await (await client.post(path, {data, headers: {'Idempotency-Key': key}})).json()) as Row;
}
const routeVersion = async () => Number((await get(`/scm/delivery/routes/${routeId}`)).route.version);
const ordersView = async () => await get<Row[]>(`/scm/delivery/routes/${routeId}/orders-view`);
const customersView = async () => await get<Row[]>(`/scm/delivery/routes/${routeId}/customers-view`);
const countOf = (rows: Row[], orderId: any) => Number(rows.find((o) => String(o.orderId) === String(orderId))?.printCount ?? -1);
const totalPrintCount = (rows: Row[]) => rows.reduce((sum, o) => sum + Number(o.printCount), 0);

test.beforeAll(async () => {
  adminToken = await login(accounts, accounts.admin);
  readOnlyToken = await login(accounts, accounts.readOnly);
  api = await apiClient(adminToken);

  // 起点仓库必须已定位且启用，否则后端会拒绝规划；从下拉选项里挑，不写死主键。
  const warehouses = await get<Row[]>('/scm/delivery/options/warehouses');
  const warehouse = warehouses.find((w) => w.status === 'ENABLED' && w.longitude != null);
  expect(warehouse, '候选仓库里需要至少一个已定位（含经纬度）且启用的仓库').toBeTruthy();

  // 订单全部自建并完成分拣：P1 之后候选池里的历史订单不会因为「已确认」就变成可配送，
  // 借用池子等于把本用例的成立与否交给上一轮留下的数据。
  const skuId = await createSku(api, routeTag, 'P');
  primaryCustomerId = await createLocatedCustomer(api, routeTag, 'A', '打印验收路A号');
  const otherCustomerId = await createLocatedCustomer(api, routeTag, 'B', '打印验收路B号');
  // 同一客户两张（构成 PARTIAL）+ 另一客户一张（证明按客户筛选不外溢）。
  picked = [
    await createDeliveryReadyOrder(api, {runTag: routeTag, customerId: primaryCustomerId, skuId,
      address: '打印验收路A号', warehouseId: warehouse!.id}),
    await createDeliveryReadyOrder(api, {runTag: routeTag, customerId: primaryCustomerId, skuId,
      address: '打印验收路A号', warehouseId: warehouse!.id}),
    await createDeliveryReadyOrder(api, {runTag: routeTag, customerId: otherCustomerId, skuId,
      address: '打印验收路B号', warehouseId: warehouse!.id}),
  ];

  routeId = String(await ok(api, 'post', '/scm/delivery/routes', {
    routeName, deliveryDate: new Date(Date.now() + 86400000).toISOString().slice(0, 10), warehouseId: warehouse!.id,
  }));
  await ok(api, 'post', `/scm/delivery/routes/${routeId}/orders`,
      {version: await routeVersion(), orderIds: picked.map((p) => p.orderId), reason: 'E2E 挂单'});

  // 挂单时停靠点沿用订单地址快照；历史订单多数没有坐标，这里走真实的选点写入路径补齐。
  const stops: Row[] = (await get(`/scm/delivery/routes/${routeId}`)).stops;
  expect(stops.length, '挂单后应按客户生成停靠点').toBe(2);
  for (const [index, stop] of stops.entries()) {
    await ok(api, 'put', `/scm/delivery/routes/${routeId}/stops/${stop.id}`, {
      version: await routeVersion(), longitude: STOP_COORDS[index].lng, latitude: STOP_COORDS[index].lat, geomCrs: 'GCJ02',
    });
  }
  await ok(api, 'post', `/scm/delivery/routes/${routeId}/plan`, {version: await routeVersion(), reason: 'E2E 规划'});
});

test.afterAll(async () => {
  try {
    // 取消线路即把 ACTIVE 指派释放回候选池，避免把已确认订单长期占死。
    if (routeId && api) await ok(api, 'post', `/scm/delivery/routes/${routeId}/cancel`, {version: await routeVersion(), reason: 'E2E 收尾：释放候选订单'});
  } finally {
    await api?.get('/login/logout');
    await api?.dispose();
    accounts.cleanup();
  }
});

test('只读视角与 GET 预览都不计次，客户聚合与订单事实同源', async () => {
  const before = await ordersView();
  expect(before.length).toBe(3);
  for (const row of before) expect(['PRINTED', 'UNPRINTED']).toContain(row.printStatus);
  await get(`/scm/delivery/routes/${routeId}/print`);
  await get(`/scm/delivery/routes/${routeId}/map`);
  const after = await ordersView();
  expect(after.map((o) => [o.orderId, o.printCount])).toEqual(before.map((o) => [o.orderId, o.printCount]));
  for (const customer of await customersView()) {
    const own = after.filter((o) => String(o.customerId) === String(customer.customerId));
    expect(customer.printedOrderCount).toBe(own.filter((o) => Number(o.printCount) > 0).length);
    expect(customer.orderCount).toBe(own.length);
    expect(customer.printStatus).toBe(customer.printedOrderCount === 0 ? 'UNPRINTED' : customer.printedOrderCount === own.length ? 'PRINTED' : 'PARTIAL');
  }
});

test('按订单登记：只有提交的订单计次，同幂等键重放不再累加，越界订单被拒', async () => {
  const [first, second] = picked;
  const before = await ordersView();
  const key = randomUUID();
  const result = await ok<Row>(api, 'post', `/scm/delivery/routes/${routeId}/print/orders`,
      {version: await routeVersion(), orderIds: [first.orderId]}, key);
  expect(result.orderCount).toBe(1);
  const after = await ordersView();
  expect(countOf(after, first.orderId)).toBe(countOf(before, first.orderId) + 1);
  expect(countOf(after, second.orderId), '未提交的订单不得被顺带计次').toBe(countOf(before, second.orderId));
  await ok<Row>(api, 'post', `/scm/delivery/routes/${routeId}/print/orders`,
      {version: await routeVersion(), orderIds: [first.orderId]}, key);
  expect(countOf(await ordersView(), first.orderId), '同 Idempotency-Key 重放不得重复计次').toBe(countOf(after, first.orderId));
  // 提交集含不属于本线路 ACTIVE 集合的订单，说明清单已过期，必须整笔拒绝
  const stale = await envelope(api, `/scm/delivery/routes/${routeId}/print/orders`,
      {version: await routeVersion(), orderIds: [first.orderId, -1]});
  expect(stale.ok, '越界订单集合应被拒绝').toBe(false);
  expect(countOf(await ordersView(), first.orderId), '被拒请求不得留下计次').toBe(countOf(after, first.orderId));
});

test('按客户登记：PARTIAL 客户只补未打印订单，已打印订单与其它客户不动', async () => {
  const before = await ordersView();
  const unprintedOfPrimary = before.filter((o) => String(o.customerId) === primaryCustomerId && Number(o.printCount) === 0);
  const printedOfPrimary = before.filter((o) => String(o.customerId) === primaryCustomerId && Number(o.printCount) > 0);
  const otherOrders = before.filter((o) => String(o.customerId) !== primaryCustomerId);
  expect(printedOfPrimary.length, '主客户需要先处于 PARTIAL（部分订单已打印）').toBeGreaterThan(0);
  expect(unprintedOfPrimary.length).toBeGreaterThan(0);
  expect(otherOrders.every((o) => Number(o.printCount) === 0), '另一客户此时应整体未打印').toBe(true);

  const result = await ok<Row>(api, 'post', `/scm/delivery/routes/${routeId}/print/customers`,
      {version: await routeVersion(), customerStatusFilter: 'PARTIAL', orderPrintFilter: 'UNPRINTED'});
  expect(result.orderCount).toBe(unprintedOfPrimary.length);
  const after = await ordersView();
  for (const order of unprintedOfPrimary) {
    expect(countOf(after, order.orderId), 'PARTIAL 客户的未打印订单应各计一次').toBe(1);
  }
  for (const order of printedOfPrimary) {
    expect(countOf(after, order.orderId), '已打印订单不得被重打').toBe(Number(order.printCount));
  }
  for (const order of otherOrders) {
    expect(countOf(after, order.orderId), '未纳入 PARTIAL 范围的客户不受影响').toBe(0);
  }
  expect((await customersView()).find((c) => String(c.customerId) === primaryCustomerId).printStatus).toBe('PRINTED');

  // 没有 PARTIAL 客户了：按同一筛选再生成必须被拒，而不是静默产出零单清单
  const empty = await envelope(api, `/scm/delivery/routes/${routeId}/print/customers`,
      {version: await routeVersion(), customerStatusFilter: 'PARTIAL', orderPrintFilter: 'UNPRINTED'});
  expect(empty.ok, '无匹配订单时应拒绝而非生成零单打印').toBe(false);
  // PARTIAL 是客户维度状态，作为订单筛选要被参数校验拦住
  const badFilter = await envelope(api, `/scm/delivery/routes/${routeId}/print/customers`,
      {version: await routeVersion(), customerStatusFilter: 'ALL', orderPrintFilter: 'PARTIAL'});
  expect(badFilter.code, badFilter.msg).toBe(30001);
  // 不给客户名单也不给状态范围时，禁止无选择地重打整条线路
  const unscoped = await envelope(api, `/scm/delivery/routes/${routeId}/print/customers`, {version: await routeVersion()});
  expect(unscoped.ok, 'ALL + 空名单必须被拒').toBe(false);
  expect(totalPrintCount(await ordersView())).toBe(totalPrintCount(after));
});

test('打印不改线路状态、不确认发货', async () => {
  expect((await get(`/scm/delivery/routes/${routeId}`)).route.status).toBe('PLANNED');
  for (const order of picked) {
    expect((await get(`/scm/order/detail/${order.orderId}`)).status, '打印不等于发货确认').toBe('CONFIRMED');
  }
});

test('缺 scm:delivery:route:print 的只读账号：读视角放行、打印与规划被服务端拒', async () => {
  const readOnly = await apiClient(readOnlyToken);
  const countsBefore = await ordersView();
  try {
    expect((await (await readOnly.get('/scm/delivery/routes?pageNum=1&pageSize=20')).json()).code).toBe(0);
    await get<Row[]>(`/scm/delivery/routes/${routeId}/orders-view`, readOnly);
    await get<Row[]>(`/scm/delivery/routes/${routeId}/customers-view`, readOnly);
    // GET 预览同样受 print 权限保护，不能靠前端隐藏按钮当权限
    expect((await (await readOnly.get(`/scm/delivery/routes/${routeId}/print`)).json()).code).toBe(30005);
    for (const [path, data] of [
      [`/scm/delivery/routes/${routeId}/print/orders`, {version: await routeVersion(), orderIds: [picked[0].orderId]}],
      [`/scm/delivery/routes/${routeId}/print/customers`, {version: await routeVersion(), customerStatusFilter: 'UNPRINTED'}],
      [`/scm/delivery/routes/${routeId}/plan`, {version: await routeVersion(), reason: '越权'}],
    ] as [string, Row][]) {
      expect((await envelope(readOnly, path, data)).code, `${path} 必须被权限拦截`).toBe(30005);
    }
    expect(totalPrintCount(await ordersView()), '被拒请求不得改变计次').toBe(totalPrintCount(countsBefore));
  } finally {
    await readOnly.dispose();
  }
});

test('页面接线：打印标签双视角可切换，登记只发计次请求且不碰库存', async ({page}) => {
  await authenticate(page, adminToken);
  const writes: string[] = [];
  page.on('request', (req) => {
    if (req.method() !== 'GET') writes.push(`${req.method()} ${new URL(req.url()).pathname}`);
  });
  await page.goto('/#/delivery/routes');
  await expect(page.locator('#scm-delivery-route-table')).toBeVisible();
  await page.getByPlaceholder('线路名称 / 编号').fill(routeName);
  await page.keyboard.press('Enter');
  const row = page.locator('#scm-delivery-route-table tbody tr.ant-table-row').filter({hasText: routeName});
  await expect(row).toHaveCount(1);
  await row.getByRole('button', {name: accessibleName('详情')}).click();

  const drawer = page.locator('.ant-drawer:visible');
  const pane = drawer.locator('.ant-tabs-tabpane-active');
  await drawer.getByRole('tab', {name: accessibleName('配送打印')}).click();
  await expect(pane.getByText('不代表发货确认')).toBeVisible();
  // 默认「按订单」视角：未勾选任何行时登记按钮必须禁用，避免无选择地重打整条线路
  const record = pane.getByRole('button', {name: /^生\s*成\s*打\s*印/});
  await expect(record).toBeDisabled();
  // 「已登记打印」两次点击文案相同，靠文案等待会让第一次的 toast 冒充第二次；只有各自的响应才能证明登记真的发了
  const register = async (kind: 'orders' | 'customers') => {
    const [response] = await Promise.all([
      page.waitForResponse((res) => res.url().includes(`/print/${kind}`) && res.request().method() === 'POST'),
      record.click(),
    ]);
    const body = await response.json();
    expect(body.code, `页面登记的 POST /print/${kind} 应成功：${body.msg}`).toBe(0);
  };
  const before = await ordersView();
  await pane.locator('tbody tr.ant-table-row .ant-table-selection-column .ant-checkbox').first().click();
  await expect(record).toBeEnabled();
  await expect(record).toContainText('登记 1');
  await register('orders');
  expect(totalPrintCount(await ordersView()), '页面登记只加一次').toBe(totalPrintCount(before) + 1);

  // 切到「按客户」：状态与订单范围两个筛选同时生效，未勾选时按状态范围提交
  await pane.getByText('按客户', {exact: true}).click();
  const selects = pane.locator('.smart-table-btn-block .ant-select');
  await expect(selects).toHaveCount(2);
  await expect(record).toBeDisabled();
  await selects.first().locator('.ant-select-selector').click();
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option-content', {hasText: '未打印客户'}).click();
  await selects.nth(1).locator('.ant-select-selector').click();
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option-content', {hasText: '仅未打印订单'}).click();
  await expect(record).toBeEnabled();
  const customersBefore = await customersView();
  const unprintedCustomer = customersBefore.find((c) => c.printStatus === 'UNPRINTED');
  expect(unprintedCustomer, '另一客户此时应整体未打印').toBeTruthy();
  const printedBefore = await ordersView();
  await register('customers');
  const printedAfter = await ordersView();
  const ownOrders = printedAfter.filter((o) => String(o.customerId) === String(unprintedCustomer.customerId));
  expect(ownOrders.every((o) => Number(o.printCount) === 1), '未打印客户的订单各计一次').toBe(true);
  for (const order of printedBefore.filter((o) => String(o.customerId) !== String(unprintedCustomer.customerId))) {
    expect(countOf(printedAfter, order.orderId), '已打印客户不受影响').toBe(Number(order.printCount));
  }
  await drawer.locator('.ant-drawer-close').click();
  // a-drawer 关闭后根节点仍挂在 body 上（destroy-on-close 只丢内容），收起状态看 ant-drawer-open
  await expect(page.locator('.ant-drawer-open')).toHaveCount(0);
  // 全程只有两次打印登记：任何其它 SCM 写接口（库存、出库、发货）都不该被页面偷偷发起。
  // 底座 Layout 自身的非业务请求（如 /support/feedback/query 轮询）不在这条约束的语义范围内。
  const scmWrites = writes.filter((u) => u.includes(' /scm/'));
  expect(scmWrites.every((u) => /^POST \/scm\/delivery\/routes\/\d+\/print\/(orders|customers)$/.test(u)), scmWrites.join('\n')).toBe(true);
  expect(new Set(scmWrites).size).toBe(2);
  await page.screenshot({path: '../.runtime/w5-delivery-print-tab.png', fullPage: true});
});
