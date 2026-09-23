/*
  Wave 4 §6.1 / §6.2 业务待办与站内提醒 E2E：真实后端 + 真实浏览器 + 自建临时账号与单据。

  钉死四件此前只在「代码看起来对」层面的事：
    1. 待办卡片的数字与目标列表接口 total 同源（同一套筛选口径，不是各算一遍）；
    2. 卡片 route 上的筛选条件被目标页面**真正吃进查询请求**（§6.1 的原始断层）；
    3. keep-alive 复用：普通菜单进入不残留旧 deep-link，再次跳入要重新套用新条件；
    4. 报损报溢驳回消息按 messageType + dataId 直达目标单据，且跳转不绕过目标接口权限。

  原文件靠外部注入 W4_E2E_ADMIN_TOKEN 门控，缺令牌即整组 skip —— 正是 §12 指出的
  「E2E 文件存在但未跑」。现在它自己建临时账号（含「只读面 − loss-gain:query」扣权账号）、
  自己造单据，收尾取消线路并删除账号。令牌与口令只存内存。
*/
import {type APIRequestContext, type Page} from '@playwright/test';
import {expect, test} from './scm-test-base';
import {randomUUID} from 'node:crypto';
import {apiClient, accessibleName, authenticate, login, provisionTempAccounts, type TempAccounts} from './scm-e2e-account';

type Row = Record<string, any>;

/** 扣掉 `scm:inventory:loss-gain:query`：§6.2 要求「旧消息还在但权限已失去」时跳转仍被服务端拒。 */
const accounts: TempAccounts = provisionTempAccounts('w4', ['scm:inventory:loss-gain:query']);
const runName = accounts.admin;
const draftRouteName = runName + '待办线路';

/** 卡片 key → 目标页面路由 + 该卡片数字所解释的列表接口。 */
const CARDS: Record<string, {page: string; method: 'get' | 'post'; queryPath: string}> = {
  'inventory-warning': {page: '/inventory/inventory-warning-list', method: 'post', queryPath: '/scm/inventory/warning/query'},
  'receipt-putaway': {page: '/purchase/purchase-receipt-list', method: 'post', queryPath: '/scm/purchase/receipt/query'},
  'loss-gain-audit': {page: '/inventory/inventory-loss-gain-list', method: 'post', queryPath: '/scm/inventory/loss-gain/query'},
  'delivery-route-draft': {page: '/delivery/routes', method: 'get', queryPath: '/scm/delivery/routes'},
};

const LOSS_GAIN_QUERY_PATH = CARDS['loss-gain-audit'].queryPath;

let api: APIRequestContext;
let adminToken = '';
let deniedToken = '';
let warehouseId = '';
let skuId = '';
let lossGainId = '';
let lossGainNo = '';
let draftRouteId = '';

test.describe.configure({mode: 'serial'});

async function ok<T = Row>(client: APIRequestContext, method: 'get' | 'post' | 'put', path: string, data?: unknown, key = randomUUID()): Promise<T> {
  const response = method === 'get'
      ? await client.get(path)
      : await client[method](path, {data, headers: {'Idempotency-Key': key}});
  const body = await response.json();
  expect(body.code, `${method.toUpperCase()} ${path}: ${body.msg}`).toBe(0);
  return body.data as T;
}
const get = <T = Row>(path: string, client: APIRequestContext = api) => ok<T>(client, 'get', path);
const post = <T = Row>(path: string, data?: unknown, client: APIRequestContext = api) => ok<T>(client, 'post', path, data);
/** 反例只取信封，不预设成功。 */
async function envelope(client: APIRequestContext, method: 'get' | 'post', path: string, data?: unknown) {
  const response = method === 'get' ? await client.get(path) : await client.post(path, {data});
  return await response.json() as Row;
}

/** 卡片 route → 页面路径 + 条件；条件必须由页面吃进去，不能只是 URL 好看。 */
function splitRoute(route: string) {
  const [page, search = ''] = route.split('?');
  return {page, params: Object.fromEntries(new URLSearchParams(search)) as Row};
}

/**
 * 取页面**自己发出的**列表查询条件：POST 读请求体、GET 读 query。
 * 断言这个而不是 DOM 上的选中态，才能证明 URL 条件真的进了后端查询口径。
 */
async function firedQuery(page: Page, path: string, method: 'get' | 'post'): Promise<Row> {
  // Playwright 的 Request.method() 是大写动词，直接和小写字面量比会永远等不到
  const request = await page.waitForRequest((req) => req.method().toLowerCase() === method && new URL(req.url()).pathname === path);
  return request.method().toLowerCase() === 'post'
      ? JSON.parse(request.postData() ?? '{}') as Row
      : Object.fromEntries(new URL(request.url()).searchParams) as Row;
}
async function openPage(page: Page, href: string, path: string, method: 'get' | 'post' = 'post'): Promise<Row> {
  const [conditions] = await Promise.all([firedQuery(page, path, method), page.goto(href)]);
  return conditions;
}

/** 自建一个非标品 SKU：报损报溢明细要有真实 skuId，不借用共享库里来历不明的商品。 */
async function newSku(): Promise<string> {
  let parentId: string | null = null;
  // SPU 只允许挂在三级 ENABLED 分类下，种子分类只播到二级，因此自己补一条 1→2→3 链
  for (const level of [1, 2, 3]) {
    parentId = String(await post('/scm/product/category/add', {
      parentId, categoryCode: `${runName}-L${level}`.toUpperCase(), name: `${runName}分类${level}`, sortOrder: 0, status: 'ENABLED',
    }));
  }
  const code = `${runName}-W4`.toUpperCase();
  await post('/scm/product/add', {
    spuCode: code, name: `${runName}待办商品`, categoryId: parentId, status: 'ON_SHELF', images: [],
    skuList: [{
      skuCode: code, specName: '散装', specValues: {规格: '散装'}, saleUnit: 'kg', productType: 'NON_STANDARD',
      marketPrice: '3.5000', status: 'ON_SHELF', defaultFlag: true, sortOrder: 0,
    }],
  });
  const options = (await post<Row>('/scm/product/sku/option-list', {keyword: code, limit: 10})).options as Row[];
  const hit = options.find((o) => o.skuCode === code);
  expect(hit, `新建 SKU ${code} 应能查到`).toBeTruthy();
  return String(hit!.skuId);
}

const routeVersion = async () => Number((await get(`/scm/delivery/routes/${draftRouteId}`)).route.version);

test.beforeAll(async () => {
  adminToken = await login(accounts, accounts.admin);
  deniedToken = await login(accounts, accounts.denied!);
  api = await apiClient(adminToken);

  const warehouses = await get<Row[]>('/scm/warehouse/list');
  const warehouse = warehouses.find((w) => w.status === 'ENABLED');
  expect(warehouse, '需要一个启用仓库来造报损单与草稿线路').toBeTruthy();
  warehouseId = String(warehouse!.id);
  skuId = await newSku();

  lossGainId = String(await post('/scm/inventory/loss-gain/create', {
    adjustType: 'LOSS', warehouseId: Number(warehouseId), reason: '待办用例：到货变质', remark: runName,
    items: [{skuId: Number(skuId), quantity: '1.0000'}],
  }));
  lossGainNo = String((await get(`/scm/inventory/loss-gain/detail/${lossGainId}`)).lossGainNo);

  draftRouteId = String(await post('/scm/delivery/routes', {
    routeName: draftRouteName, deliveryDate: new Date(Date.now() + 86400000).toISOString().slice(0, 10), warehouseId: Number(warehouseId),
  }));
});

test.afterAll(async () => {
  try {
    // 草稿线路留在库里会一直抬高「草稿配送线路」待办数字，收尾取消它
    if (draftRouteId && api) await ok(api, 'post', `/scm/delivery/routes/${draftRouteId}/cancel`, {version: await routeVersion(), reason: 'E2E 收尾'});
  } finally {
    await api?.get('/login/logout');
    await api?.dispose();
    accounts.cleanup();
  }
});

test('待办卡片数字与目标列表接口 total 同源，route 落在目标页面', async () => {
  const cards = await get<Row[]>('/scm/dashboard/todo');
  const keys = cards.map((card) => card.key);
  expect(keys, 'admin 应看到全部四张卡片').toEqual(Object.keys(CARDS));
  for (const card of cards) {
    const target = CARDS[card.key];
    const {page, params} = splitRoute(card.route);
    expect(page, `${card.key} 的 route 必须指向目标列表页`).toBe(target.page);
    const query = {...params, pageNum: 1, pageSize: 1};
    const total = target.method === 'get'
        ? (await get<Row>(`${target.queryPath}?${new URLSearchParams(query as Record<string, string>)}`)).total
        : (await post(target.queryPath, query)).total;
    // 同一条筛选口径算出来的两个数：待办不是第二套统计
    expect(Number(total), `${card.key} 待办数字应等于列表 total`).toBe(Number(card.count));
  }
  // 本组造的单据让上面那条断言不再是 0 == 0
  expect(Number(cards.find((c) => c.key === 'loss-gain-audit')!.count), '刚造的待审核报损单要被计入').toBeGreaterThanOrEqual(1);
  expect(Number(cards.find((c) => c.key === 'delivery-route-draft')!.count), '刚造的草稿线路要被计入').toBeGreaterThanOrEqual(1);
});

test('§6.1-1 待确认入库卡片：status / receiptMode / putawayStatus 三个条件全部落到查询请求', async ({page}) => {
  await authenticate(page, adminToken);
  const card = (await get<Row[]>('/scm/dashboard/todo')).find((c) => c.key === 'receipt-putaway')!;
  const {page: path, params} = splitRoute(card.route);
  const conditions = await openPage(page, `/#${path}?${new URLSearchParams(params as Record<string, string>)}`, CARDS['receipt-putaway'].queryPath);
  expect(conditions.status, '收货状态筛选没进查询条件').toBe('CONFIRMED');
  expect(conditions.receiptMode, '入库模式筛选没进查询条件').toBe('WAREHOUSE_CONFIRM');
  expect(conditions.putawayStatus, '上架状态筛选没进查询条件').toBe('PENDING');
});

test('§6.1-2 首页点待审批卡片：地址栏带条件、查询带条件、列表就是那几张单', async ({page}) => {
  await authenticate(page, adminToken);
  const cards = await get<Row[]>('/scm/dashboard/todo');
  const card = cards.find((c) => c.key === 'loss-gain-audit')!;
  await page.goto('/#/home');
  const todoCard = page.locator('.ant-card').filter({hasText: '业务待办'});
  await expect(todoCard).toBeVisible();
  const row = todoCard.locator('.todo-row').filter({hasText: card.label});
  // 卡片上的徽标数字就是接口给的 count，不是前端另算一份
  await expect(row.locator('.ant-badge-count')).toHaveText(String(card.count));
  const [conditions] = await Promise.all([firedQuery(page, LOSS_GAIN_QUERY_PATH, 'post'), row.click()]);
  expect(conditions.status, '点击卡片后页面查询未按 PENDING 收窄').toBe('PENDING');
  await expect(page).toHaveURL(/inventory-loss-gain-list\?status=PENDING/);
  await expect(page.locator('#scm-inventory-loss-gain-table')).toContainText(lossGainNo);
});

test('§6.1-3 草稿配送线路卡片：status=DRAFT 落到列表查询', async ({page}) => {
  await authenticate(page, adminToken);
  const card = (await get<Row[]>('/scm/dashboard/todo')).find((c) => c.key === 'delivery-route-draft')!;
  const {page: path, params} = splitRoute(card.route);
  const conditions = await openPage(page, `/#${path}?${new URLSearchParams(params as Record<string, string>)}`, CARDS['delivery-route-draft'].queryPath, 'get');
  expect(conditions.status, '线路状态筛选没进查询条件').toBe('DRAFT');
  await expect(page.locator('#scm-delivery-route-table')).toContainText(draftRouteName);
});

test('§6.1-4 从 deep-link 退回普通菜单入口：不残留上次的筛选条件', async ({page}) => {
  await authenticate(page, adminToken);
  const withFilter = await openPage(page, '/#/inventory/inventory-loss-gain-list?status=PENDING', LOSS_GAIN_QUERY_PATH);
  expect(withFilter.status).toBe('PENDING');
  const plain = await openPage(page, '/#/inventory/inventory-loss-gain-list', LOSS_GAIN_QUERY_PATH);
  // 组件被缓存时最容易犯的错是「query 没了但表单还留着 PENDING」
  expect(plain.status ?? null, '普通菜单进入仍带着上一次 deep-link 的状态').toBeNull();
});

test('§6.1-5 keep-alive 复用时重新套用新 query', async ({page}) => {
  await authenticate(page, adminToken);
  expect((await openPage(page, '/#/inventory/inventory-loss-gain-list?status=PENDING', LOSS_GAIN_QUERY_PATH)).status).toBe('PENDING');
  // 先离开再跳回：走的是组件缓存后重新进入的路径，不是首次挂载
  await page.goto('/#/delivery/routes');
  await page.waitForRequest((req) => new URL(req.url()).pathname === CARDS['delivery-route-draft'].queryPath);
  const again = await openPage(page, '/#/inventory/inventory-loss-gain-list?status=REJECTED', LOSS_GAIN_QUERY_PATH);
  expect(again.status, '第二次跳入没有套用新条件').toBe('REJECTED');
});

test('§6.2 报损报溢驳回消息：按业务标识直达目标单据，不靠中文标题猜业务', async ({page}) => {
  await post(`/scm/inventory/loss-gain/reject/${lossGainId}`, {
    version: (await get(`/scm/inventory/loss-gain/detail/${lossGainId}`)).version, auditOpinion: '数量与验收单不符，请核对',
  });
  expect((await get(`/scm/inventory/loss-gain/detail/${lossGainId}`)).status).toBe('REJECTED');

  // 消息落库必须带 messageType + dataId：前端据此决定跳转，解析标题只是文案
  const messages = await post<Row>('/support/message/queryMyMessage', {pageNum: 1, pageSize: 10, searchWord: lossGainNo});
  const message = (messages.list as Row[]).find((m) => String(m.dataId) === lossGainId);
  expect(message, '驳回后应给录单人留一条带 dataId 的消息').toBeTruthy();
  expect(Number(message!.messageType), '消息业务标识应为报损报溢').toBe(3);

  await authenticate(page, adminToken);
  await page.goto('/#/account?menuId=message');
  await page.getByPlaceholder('标题/内容').fill(lossGainNo);
  // 原生消息页的查询按钮里带 SearchOutlined，图标自身的 aria-label 会混进可及名，只能按子串匹配
  await page.getByRole('button', {name: /查\s*询/}).click();
  // 列表只渲染【类型】+ 标题，单号仅存在于 content（服务端 searchWord 按 title/content 匹配），
  // 所以按单号过滤行永远选不中；唯一性由上面的 searchWord 保证。
  // 标题元素是无 href 的 <a>（不算 role=link），且已读/未读两套样式用 v-show 各留一个节点，只取可见的那个。
  await page.locator('a:visible').filter({hasText: '报损报溢单被驳回'}).click();
  const detail = page.locator('.ant-drawer-open').filter({hasText: '消息内容'});
  await expect(detail).toBeVisible();
  await detail.getByRole('button', {name: accessibleName('查看业务单据')}).click();
  await expect(page).toHaveURL(new RegExp(`inventory-loss-gain-list\\?id=${lossGainId}$`));
  const business = page.locator('.ant-drawer-open').filter({hasText: '报损报溢单详情'});
  await expect(business).toBeVisible();
  await expect(business).toContainText(lossGainNo);
});

test('§6.2 失去目标权限的账号跳同一链接：接口仍被服务端拒，不靠 UI 隐藏', async () => {
  const denied = await apiClient(deniedToken);
  try {
    expect((await envelope(denied, 'post', LOSS_GAIN_QUERY_PATH, {pageNum: 1, pageSize: 20})).code, '列表查询应被权限拦截').toBe(30005);
    expect((await envelope(denied, 'get', `/scm/inventory/loss-gain/detail/${lossGainId}`)).code, '详情深链应被权限拦截').toBe(30005);
  } finally {
    await denied.dispose();
  }
});
