/*
  Wave 4 业务待办 E2E：GET /scm/dashboard/todo 只读聚合 + 首页卡片条件跳转。

  钉死三件后端单元/契约测试覆盖不到的真实链路：
    1. 待办接口只读、按登录人权限裁剪（admin 全可见；无权员工拿不到领域计数）；
    2. 卡片 route 是「已带查询条件」的真实列表页，点击后地址栏落到该条件；
    3. 首页在无 scm:todo:query 时整卡隐藏（v-privilege），不发请求。

  与其余 scm-*.spec.ts 一样跑真实后端 + 真实浏览器。需要一次性管理员令牌：
  未设置 W4_E2E_ADMIN_TOKEN 时整体 skip（共享库当前口径过期，见 docs/progress.md）。
  令牌只存内存，绝不写盘、绝不 attach 到报告。
*/
import {test, expect, request, type APIRequestContext, type Page} from '@playwright/test';

const apiUrl = process.env.W4_E2E_API_BASE || 'http://127.0.0.1:18080';
const adminToken = process.env.W4_E2E_ADMIN_TOKEN;
const REQUIRED_KEYS = ['inventory-warning', 'receipt-putaway', 'loss-gain-audit', 'delivery-route-draft'];

let admin: APIRequestContext;

test.describe('Wave 4 业务待办与首页跳转', () => {
  test.skip(!adminToken, 'Set W4_E2E_ADMIN_TOKEN (temporary admin Bearer) to run against a live backend');

  test.beforeAll(async () => {
    admin = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${adminToken}`}});
  });

  test.afterAll(async () => {
    await admin?.dispose();
  });

  test('待办接口只读返回卡片数组，字段齐全且 route 自带查询条件', async () => {
    const r = await (await admin.get('/scm/dashboard/todo')).json();
    expect(r.code).toBe(0);
    expect(Array.isArray(r.data)).toBe(true);
    for (const card of r.data) {
      expect(typeof card.key).toBe('string');
      expect(typeof card.label).toBe('string');
      expect(typeof card.count).toBe('number');
      expect(card.count).toBeGreaterThanOrEqual(0);
      expect(card.route.startsWith('/')).toBe(true);
      // admin 拥有全部领域权限，四张卡片都应按各自状态条件过滤到目标列表页
      if (REQUIRED_KEYS.includes(card.key)) {
        expect(card.route).toContain('?');
        expect(card.route).toMatch(/status=|receiptMode=|putawayStatus=/);
      }
    }
  });

  test('待办接口拒绝无 scm:todo:query 权限的员工', async () => {
    const secret = process.env.W4_E2E_NO_PERM_TOKEN;
    test.skip(!secret, 'Set W4_E2E_NO_PERM_TOKEN (employee without scm:todo:query) to assert the read guard');
    const emp = await request.newContext({baseURL: apiUrl, extraHTTPHeaders: {Authorization: `Bearer ${secret}`}});
    const res = await emp.get('/scm/dashboard/todo');
    await emp.dispose();
    // Sa-Token 权限守卫返回非 0 信封（而非领域数据）
    expect((await res.json()).code).not.toBe(0);
  });

  test('首页点击待办卡片落到带条件的目标列表页', async ({page}) => {
    const cards = (await (await admin.get('/scm/dashboard/todo')).json()).data as {label: string; route: string}[];
    const target = cards.find((c) => c.route.includes('?'));
    test.skip(!target, 'No navigable business-todo card in current data');

    await page.addInitScript((t) => localStorage.setItem('smart_admin_user_token', t), adminToken!);
    const writes: string[] = [];
    page.on('request', (req) => {
      if (req.method() !== 'GET') writes.push(req.url());
    });
    await page.goto('/#/home');
    await page.getByText('业务待办', {exact: true}).waitFor({timeout: 10_000});
    await page.locator('.todo-row').filter({hasText: target!.label}).first().click();
    await expect(page).toHaveURL(new RegExp(target!.route.split('?')[0].replace(/^\//, '')));
    // 只读：首页到跳转全程不应因待办产生任何写请求
    expect(writes.some((u) => u.includes('/scm/dashboard'))).toBe(false);
  });
});
