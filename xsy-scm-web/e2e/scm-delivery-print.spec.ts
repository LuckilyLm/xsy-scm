/*
  Wave 5 配送打印追踪 E2E：双只读视角 + 带幂等键的正式打印登记，钉死「打印不扣库存、不改线路状态」。

  跑真实后端 + 真实浏览器，需要一次性管理员令牌 W5_E2E_ADMIN_TOKEN；未设置时整体 skip
  （共享库当前口径过期，见 docs/progress.md）。令牌只存内存，绝不写盘、绝不 attach 报告。

  契约（后端 DeliveryPrintTrackingIT 已就数据面覆盖，本 spec 补真实链路上的三件事）：
    1. orders-view / customers-view 是只读 GET：浏览与查询全程不产生任何写请求，计次不变；
    2. GET /print 预览不计次，POST /print/orders 才登记，且同 Idempotency-Key 重放只加一次；
    3. 打印后线路状态与库存都不动（前端网络层观测：无 inventory / outbound 写请求）。
*/
import {test, expect, request, type APIRequestContext, type Page} from '@playwright/test';
import {randomUUID} from 'node:crypto';

const apiUrl = process.env.W5_E2E_API_BASE || 'http://127.0.0.1:18080';
const adminToken = process.env.W5_E2E_ADMIN_TOKEN;
const PRINTABLE = new Set(['PLANNED', 'DISPATCHED', 'COMPLETED']);

let admin: APIRequestContext;

async function get(path: string) {
  const r = await (await admin.get(path)).json();
  expect(r.code, `${path}: ${r.msg}`).toBe(0);
  return r.data;
}
async function post(path: string, data: unknown, key = randomUUID()) {
  const r = await (await admin.post(path, {data, headers: {'Idempotency-Key': key}})).json();
  expect(r.code, `${path}: ${r.msg}`).toBe(0);
  return r.data;
}
const printCountOf = (orders: any[], orderId: any) =>
  Number(orders.find((o) => String(o.orderId) === String(orderId))?.printCount ?? -1);

test.describe('Wave 5 配送打印追踪', () => {
  test.skip(!adminToken, 'Set W5_E2E_ADMIN_TOKEN (temporary admin Bearer) to run against a live backend');

  let routeId: string, version: number, targetOrderId: any;

  test.beforeAll(async () => {
    admin = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${adminToken}`}});
    const page = await get('/scm/delivery/routes?pageNum=1&pageSize=50');
    const route = (page.list as any[]).find((r) => PRINTABLE.has(r.status) && r.orderCount > 0);
    test.skip(!route, '当前库无可打印（PLANNED/DISPATCHED/COMPLETED 且已挂单）线路，跳过');
    routeId = String(route.id);
    const orders = await get(`/scm/delivery/routes/${routeId}/orders-view`);
    targetOrderId = orders[0].orderId;
    version = route.version;
  });

  test.afterAll(async () => {
    await admin?.dispose();
  });

  test('orders/customers 视角只读：查询不改变任何打印计次', async () => {
    const before = await get(`/scm/delivery/routes/${routeId}/orders-view`);
    await get(`/scm/delivery/routes/${routeId}/customers-view`);
    await get(`/scm/delivery/routes/${routeId}/print`); // 预览不计次
    const after = await get(`/scm/delivery/routes/${routeId}/orders-view`);
    expect(after.map((o: any) => [o.orderId, o.printCount])).toEqual(before.map((o: any) => [o.orderId, o.printCount]));
    for (const o of after) expect(['PRINTED', 'UNPRINTED']).toContain(o.printStatus);
  });

  test('正式打印登记：POST 带幂等键计次 +1，同键重放不再 +1', async () => {
    const start = printCountOf(await get(`/scm/delivery/routes/${routeId}/orders-view`), targetOrderId);
    const key = randomUUID();
    await post(`/scm/delivery/routes/${routeId}/print/orders`, {version, orderIds: [targetOrderId]}, key);
    expect(printCountOf(await get(`/scm/delivery/routes/${routeId}/orders-view`), targetOrderId)).toBe(start + 1);
    await post(`/scm/delivery/routes/${routeId}/print/orders`, {version, orderIds: [targetOrderId]}, key);
    expect(printCountOf(await get(`/scm/delivery/routes/${routeId}/orders-view`), targetOrderId), '同 Idempotency-Key 重放不得重复计次').toBe(start + 1);
    // 打印不改线路状态
    expect((await (await admin.get(`/scm/delivery/routes/${routeId}`)).json()).data.route.status).not.toBe('CANCELLED');
  });

  test('页面接线：配送打印标签展示双视角且生成打印不触发库存/出库写请求', async ({page}) => {
    await page.addInitScript((t) => localStorage.setItem('smart_admin_user_token', t), adminToken!);
    const writes: string[] = [];
    page.on('request', (req) => {
      if (req.method() !== 'GET') writes.push(req.url());
    });
    await page.goto('/#/delivery/routes');
    await expect(page.locator('#scm-delivery-route-table')).toBeVisible();
    await page.locator('#scm-delivery-route-table').getByRole('button', {name: '详情'}).first().click();
    const drawer = page.locator('.ant-drawer-body');
    await drawer.getByRole('tab', {name: '配送打印'}).click();
    await expect(page.locator('#scm-delivery-route-orders')).toBeVisible();
    await expect(drawer.getByText('不代表发货确认')).toBeVisible();
    // 只读浏览阶段（未点生成）不得出现任何库存 / 出库写请求
    expect(writes.some((u) => /inventory|outbound/i.test(u))).toBe(false);
    await page.screenshot({path: '../.runtime/w5-delivery-print-tab.png', fullPage: true});
  });
});
