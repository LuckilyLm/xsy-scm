/*
 * Wave 7 客户 360° E2E（只读上下文聚合）
 *
 * 来源：**W6 派生** —— token 门控、env 可覆盖地址、深链与网络断言风格照抄
 * `e2e/scm-stocktake-import.spec.ts`；不依赖账号脚本，直接对现有后端跑关键路径。
 *
 * 这一页的聚合口径（仅 CONFIRMED、按 SKU+单位分组、订购量非结算量、最近价不兜底）已在
 * CustomerFrequentSkuIT 用真实 SQL 证明，因此 E2E 只验证**前端接线**：
 * - 客户详情渲染出 5 个只读上下文 Tab；
 * - 切到「常购商品」确实以 **GET** 命中 `/frequent-skus` 且返回业务码 0（只读聚合，不是写命令）；
 * - 全程无 pageerror。
 *
 * 需要真实后端与一个临时管理员 Bearer，故用 W7_E2E_ADMIN_TOKEN 门控；缺省即整组跳过。
 */
import {test, expect, request, type APIRequestContext} from '@playwright/test';

const apiUrl = process.env.W7_E2E_API_BASE || 'http://127.0.0.1:18080';
const webUrl = process.env.W7_E2E_WEB_BASE || 'http://127.0.0.1:8080';
const adminToken = process.env.W7_E2E_ADMIN_TOKEN;

// antd 会在两个 CJK 字符间插入视觉空格，Tab 的无障碍名按字符间可选空白匹配，避免退化成子串。
const escapeRe = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const tabName = (text: string) => new RegExp('^' + [...text].map(escapeRe).join('\\s*') + '$');

let admin: APIRequestContext;

test.describe('Wave 7 客户 360° 只读上下文', () => {
  test.skip(!adminToken, 'Set W7_E2E_ADMIN_TOKEN (temporary admin Bearer) to run against a live backend');

  test.beforeAll(async () => {
    admin = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${adminToken}`}});
  });

  test.afterAll(async () => {
    await admin?.dispose();
  });

  test('客户详情渲染 5 个上下文 Tab，常购商品走 GET 且返回业务码 0', async ({page}) => {
    // 取一个既有客户作为上下文；无数据则跳过而非造假 ID。
    const query = await (await admin.post('/scm/customer/query', {data: {pageNum: 1, pageSize: 1}})).json();
    const customerId = query.code === 0 ? query.data?.list?.[0]?.customerId : undefined;
    test.skip(!customerId, '当前库没有任何客户，跳过');

    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.addInitScript((t) => localStorage.setItem('smart_admin_user_token', t), adminToken);
    await page.goto(`${webUrl}/#/customer/customer-detail?customerId=${customerId}`);

    // 5 个只读上下文 Tab 必须都在。
    for (const label of ['基础资料', '最近订单', '常购商品', '协议价', '可售商品']) {
      await expect(page.getByRole('tab', {name: tabName(label)})).toBeVisible();
    }

    // 切到常购商品：确实以 GET 命中聚合端点，返回业务码 0（只读、非写命令）。
    const freq = page.waitForResponse((r) => r.url().includes('/frequent-skus'));
    await page.getByRole('tab', {name: tabName('常购商品')}).click();
    const res = await freq;
    expect(res.request().method()).toBe('GET');
    const body = await res.json();
    expect(body.code, JSON.stringify(body)).toBe(0);

    await page.screenshot({path: '../.runtime/w7-customer-360-frequent.png', fullPage: true});
    expect(errors).toEqual([]);
  });
});
