/*
 * Wave 7 客户 360° E2E（只读上下文聚合 + 双权限反例）
 *
 * 来源：用例结构照抄 `e2e/scm-customer.spec.ts`（临时账号 + 真实登录），
 * 账号与登录的共用实现见 `e2e/scm-e2e-account.ts`。
 *
 * 原先这组用例靠外部注入的一次性管理员令牌门控（缺令牌即整组 skip），
 * 于是「文件存在」被当成了「场景已验收」；现在它自建临时账号，真实跑在 dev 栈上。
 *
 * 这一页的聚合口径（仅 CONFIRMED、按 SKU+单位分组、订购量非结算量、最近价不兜底）已在
 * CustomerFrequentSkuIT 用真实 SQL 证明，因此 E2E 只验证**前端接线与权限边界**：
 * - 客户详情渲染出 5 个只读上下文 Tab；
 * - 切到「常购商品」确实以 **GET** 命中 `/frequent-skus` 且返回业务码 0（只读聚合，不是写命令）；
 * - 有客户查看权但**没有订单查看权**的账号，聚合端点必须被服务端整条拒（30005），
 *   页面落到错误态而不是把历史成交价摊开 —— 取数源是订单事实，权限是 `AND` 而不是「有客户权就能看」；
 * - 全程无 pageerror。
 */
import {test, expect, type APIRequestContext} from '@playwright/test';
import {randomUUID} from 'node:crypto';
import {accessibleName, apiClient, authenticate, login, provisionTempAccounts, type TempAccounts} from './scm-e2e-account';

const ORDER_QUERY_PERMISSION = 'scm:order:query';

let accounts: TempAccounts;
let adminToken: string;
let admin: APIRequestContext;
let customerId = 0;
let customerVersion = 0;

test.describe('Wave 7 客户 360° 只读上下文', () => {
  test.beforeAll(async () => {
    accounts = provisionTempAccounts('w7', [ORDER_QUERY_PERMISSION]);
    adminToken = await login(accounts, accounts.admin);
    admin = await apiClient(adminToken);

    const types = await (await admin.post('/scm/customer/type/query', {data: {pageNum: 1, pageSize: 1}})).json();
    expect(types.code, `查询客户类型失败：${types.msg}`).toBe(0);
    const employees = await (await admin.post('/employee/query', {data: {pageNum: 1, pageSize: 1}})).json();
    expect(employees.code, `查询员工失败：${employees.msg}`).toBe(0);
    const suffix = randomUUID().replaceAll('-', '').slice(0, 12).toUpperCase();
    // 客户编码由服务端生成（CUS + 6 位序号），创建载荷不再提交编码；用例全部按 customerId 定位。
    const created = await (await admin.post('/scm/customer/add', {data: {
      name: `W7 客户 360 ${suffix}`,
      customerTypeId: types.data.list[0].typeId,
      settleMode: 'INDEPENDENT',
      sellerId: employees.data.list[0].employeeId,
    }})).json();
    expect(created.code, `创建 Wave 7 客户夹具失败：${created.msg}`).toBe(0);
    customerId = Number(created.data);
    const detail = await (await admin.get(`/scm/customer/detail/${customerId}`)).json();
    expect(detail.code, `读取 Wave 7 客户夹具失败：${detail.msg}`).toBe(0);
    customerVersion = Number(detail.data.version);
  });

  test.afterAll(async () => {
    if (admin && customerId > 0) {
      await admin.post('/scm/customer/delete', {data: {customerId, version: customerVersion}});
    }
    await admin?.dispose();
    accounts?.cleanup();
  });

  /** 每轮自建客户夹具，干净迁移库也能执行只读上下文与权限反例。 */
  function requireCustomerId(): number {
    expect(customerId, 'Wave 7 客户夹具未创建').toBeGreaterThan(0);
    return customerId;
  }

  test('客户详情渲染 5 个上下文 Tab，常购商品走 GET 且返回业务码 0', async ({page}) => {
    const customerId = requireCustomerId();

    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await authenticate(page, adminToken);
    await page.goto(`/#/customer/customer-detail?customerId=${customerId}`);

    // 5 个只读上下文 Tab 必须都在。
    for (const label of ['基础资料', '最近订单', '常购商品', '协议价', '可售商品']) {
      await expect(page.getByRole('tab', {name: accessibleName(label)})).toBeVisible();
    }

    // 切到常购商品：确实以 GET 命中聚合端点，返回业务码 0（只读、非写命令）。
    const freq = page.waitForResponse((r) => r.url().includes('/frequent-skus'));
    await page.getByRole('tab', {name: accessibleName('常购商品')}).click();
    const res = await freq;
    expect(res.request().method()).toBe('GET');
    const body = await res.json();
    expect(body.code, JSON.stringify(body)).toBe(0);

    expect(errors).toEqual([]);
  });

  test('缺订单查看权的账号读常购商品被整条拒，页面不摊开成交价', async ({page}) => {
    const customerId = requireCustomerId();

    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const deniedToken = await login(accounts, accounts.denied!);
    const denied = await apiClient(deniedToken);
    try {
      // 前提：这个账号确实有客户查看权（否则「不泄露」只是因为根本进不来，断言没有意义）。
      const allowed = await (await denied.get(`/scm/customer/detail/${customerId}`)).json();
      expect(allowed.code, '扣权账号应仍保有 scm:customer:query').toBe(0);

      // 「客户查询 ∧ 订单查询」缺一即整条拒，且响应体不带任何价格字段。
      const rejected = await (await denied.get(`/scm/customer/${customerId}/frequent-skus?days=90&limit=20`)).json();
      expect(rejected.code, '缺订单查看权必须被服务端拒绝').toBe(30005);
      expect(JSON.stringify(rejected.data ?? null), '被拒响应不得带出任何成交数据').not.toMatch(/price|amount|qty/i);
    } finally {
      await denied.dispose();
    }

    // 同一个账号在真实页面上切到常购商品：落错误态，而不是渲染出价格表。
    await authenticate(page, deniedToken);
    await page.goto(`/#/customer/customer-detail?customerId=${customerId}`);
    // 扣权账号只挖掉 `scm:order:query` 这一枚按钮权限，详情页与 Tab 必须照常可达；
    // 若这里变成「菜单不下发落到 404」，证的就是另一件事了，必须让它红。
    const frequentTab = page.getByRole('tab', {name: accessibleName('常购商品')});
    await expect(frequentTab).toBeVisible();
    const response = page.waitForResponse((r) => r.url().includes('/frequent-skus'));
    await frequentTab.click();
    const body = await (await response).json();
    expect(body.code, '页面上的常购商品聚合必须被服务端整条拒').toBe(30005);
    await expect(page.getByText('最近成交价')).toHaveCount(0);
    expect(errors).toEqual([]);
  });
});
