/*
 * W2 客户域 E2E
 *
 * 来源：**W1 派生** —— 整体结构、登录方式（SM4 传输加密）、夹具账号生命周期、
 * CJK 按钮空格处理、请求断言风格全部照抄 `e2e/scm-product.spec.ts`。
 *
 * 覆盖的**业务不变量**（这些是 W2 相对 legacy/C 的新增能力，必须在真实链路上验证）：
 * - 账期三形态：按时间 + 单位「月」+ 固定结算日必须完整落库（`creditPeriodType/Value/Unit/settleDay`）；
 * - 授信额度是 4 位定点**字符串**，`"1234.5000"` 不能被浮点化；
 * - 新建客户状态固定 `POTENTIAL`，状态变更只能走 `updateStatus`；
 * - 客户类型被客户引用时删不掉（40938），删掉客户后才能删类型；
 * - 只读角色：接口 30005 + 「新增客户」按钮不渲染。
 */
import { test, expect, request, type Page, type Locator, type APIRequestContext } from '@playwright/test';
import { randomBytes } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import smCrypto from 'sm-crypto';

const apiUrl = 'http://127.0.0.1:18080';
const name = 'w2_e2e_' + Date.now().toString(36);
const password = 'W2@' + randomBytes(6).toString('hex');
const env = { ...process.env, W2_E2E_NAME: name, W2_E2E_PASSWORD: password };
let api: APIRequestContext;
let token: string;
const customerIds: (number | string)[] = [];
const typeIds: (number | string)[] = [];

// Ant Design inserts a visual space between two CJK characters in button labels
// ("查询" renders as "查 询"). Match the accessible name with optional whitespace
// between characters instead of weakening the assertion to a substring match.
const escapeRe = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const buttonName = (text: string) => new RegExp('^' + [...text].map(escapeRe).join('\\s*') + '$');
const button = (root: Page | Locator, text: string) => root.getByRole('button', { name: buttonName(text) });

// antd 把当前选中值渲染成 `.ant-select-selection-item`，它盖在内部 search input 之上，
// 直接点 `getByLabel(...)` 命中的 input 会被 pointer-event 拦截而超时。
// 因此统一通过表单控件的 `id`（a-form-item 带 name 时 antd 生成 `form_item_<name>`）
// 定位到 `.ant-select-selector` 再点击开下拉。
const formItem = (root: Page | Locator, field: string) => root.locator(`.ant-form-item:has(#form_item_${field})`);
const openSelect = (root: Page | Locator, field: string) => formItem(root, field).locator('.ant-select-selector').click();

async function login(account: string) {
  const client = await request.newContext({ baseURL: apiUrl });
  const captcha = (await (await client.get('/login/getCaptcha')).json()).data;
  const source = readFileSync('src/lib/encrypt.ts', 'utf8');
  const key = /const SM4_KEY = '([^']+)'/.exec(source)?.[1];
  if (!key) throw new Error('SmartAdmin transport key not found');
  const encrypted = Buffer.from(smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
  const result = await (await client.post('/login', { data: { loginName: account, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1 } })).json();
  expect(result.code, 'SmartAdmin login succeeds').toBe(0);
  await client.dispose();
  return result.data.token as string;
}

test.beforeAll(async () => {
  execFileSync('python', ['../tools/w2_e2e_accounts.py', 'setup'], { env, stdio: 'pipe' });
  token = await login(name);
  api = await request.newContext({ baseURL: apiUrl, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
});

test.afterAll(async () => {
  if (api) {
    for (const id of customerIds) {
      const detail = await (await api.get(`/scm/customer/detail/${id}`)).json();
      if (detail.code === 0) await api.post('/scm/customer/delete', { data: { customerId: id, version: detail.data.version } });
    }
    // 客户类型没有 detail 端点，版本号从列表里取。
    const list = await (await api.post('/scm/customer/type/query', { data: { pageNum: 1, pageSize: 100, keyword: name.toUpperCase() } })).json();
    for (const row of list.code === 0 ? list.data.list : []) {
      await api.post('/scm/customer/type/delete', { data: { typeId: row.typeId, version: row.version } });
    }
    await api.get('/login/logout');
    await api.dispose();
  }
  execFileSync('python', ['../tools/w2_e2e_accounts.py', 'cleanup'], { env, stdio: 'pipe' });
});

async function authenticate(page: Page, value = token) {
  await page.addInitScript((v) => localStorage.setItem('smart_admin_user_token', v), value);
}

test('live customer pilot: type, credit period, status, search, deep link and deletion', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await authenticate(page);
  const prefix = name.toUpperCase();

  // ---------------------------------------------------------------- 客户类型
  await page.goto('/#/customer/customer-type-list');
  await button(page, '新增客户类型').click();
  const modal = page.locator('.ant-modal:visible');
  await modal.getByLabel('类型编码').fill(prefix + 'T');
  await modal.getByLabel('类型名称').fill(prefix + '类型');
  const typeCreated = page.waitForResponse((r) => r.url().endsWith('/scm/customer/type/add'));
  await modal.getByRole('button', { name: /确.*定/ }).click();
  const typeBody = await (await typeCreated).json();
  expect(typeBody.code, JSON.stringify(typeBody)).toBe(0);
  typeIds.push(typeBody.data);
  await expect(modal).not.toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: prefix + 'T' })).toBeVisible();

  // ------------------------------------------------------------------ 新建客户
  await page.goto('/#/customer/customer-list');
  await button(page, '新增客户').click();
  // a-drawer keeps its root element mounted after closing (destroy-on-close only drops
  // the body), so "closed" is asserted through the ant-drawer-open state class.
  const drawer = page.locator('.ant-drawer');
  const openDrawer = page.locator('.ant-drawer-open');
  const expectDrawerClosed = () => expect(openDrawer).toHaveCount(0);

  await drawer.getByLabel('客户编码', { exact: true }).fill(prefix + 'C1');
  await drawer.getByLabel('客户名称', { exact: true }).fill(prefix + '客户甲');
  await openSelect(drawer, 'customerTypeId');
  // 客户类型下拉的选项文本是「名称 （编码）」，不是纯名称，所以不能用 exact 匹配。
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: prefix + '类型' }).click();
  await drawer.getByLabel('联系电话', { exact: true }).fill('13800138000');
  await drawer.getByLabel('授信额度', { exact: true }).fill('1234.5000');

  // 账期：按时间 / 1 / 月 / 15 —— 只有单位是「月」时才出现固定结算日。
  await openSelect(drawer, 'creditPeriodType');
  await page.locator('.ant-select-dropdown:visible').getByText('按时间', { exact: true }).click();
  await drawer.getByLabel('账期值', { exact: true }).fill('1');
  await openSelect(drawer, 'creditPeriodUnit');
  await page.locator('.ant-select-dropdown:visible').getByText('月', { exact: true }).click();
  await expect(drawer.getByLabel('固定结算日', { exact: true })).toBeVisible();
  await drawer.getByLabel('固定结算日', { exact: true }).fill('15');

  const created = page.waitForResponse((r) => r.url().endsWith('/scm/customer/add'));
  await button(drawer, '保存').click();
  const body = await (await created).json();
  expect(body.code, JSON.stringify(body)).toBe(0);
  const customerId = body.data as number;
  customerIds.push(customerId);
  await expectDrawerClosed();

  // 列表 VO 不含账期字段，必须回详情接口核对 —— 这是 W2 新增能力是否真落库的唯一证据。
  const detail = (await (await api.get(`/scm/customer/detail/${customerId}`)).json()).data;
  expect(detail.creditLimit).toBe('1234.5000');
  expect(detail.creditPeriodType).toBe('BY_TIME');
  expect(detail.creditPeriodValue).toBe(1);
  expect(detail.creditPeriodUnit).toBe('MONTH');
  expect(detail.settleDay).toBe(15);
  expect(detail.status).toBe('POTENTIAL');
  expect(detail.contactPhone).toBe('13800138000');

  // -------------------------------------------------------------- 状态变更
  let row = page.getByRole('row').filter({ hasText: prefix + 'C1' });
  await expect(row).toBeVisible();
  const statusChanged = page.waitForResponse((r) => r.url().endsWith('/scm/customer/updateStatus'));
  await button(row, '状态').click();
  await page.locator('.ant-dropdown:visible').getByText('合作中', { exact: true }).click();
  expect((await (await statusChanged).json()).code).toBe(0);
  await expect(row.getByText('合作中')).toBeVisible();

  // ------------------------------------------------------------------ 编辑
  await button(row, '编辑').click();
  await expect(drawer.getByLabel('客户编码', { exact: true })).toHaveValue(prefix + 'C1');
  await drawer.getByLabel('授信额度', { exact: true }).fill('9999.0000');
  const updated = page.waitForResponse((r) => r.url().endsWith('/scm/customer/update'));
  await button(drawer, '保存').click();
  expect((await (await updated).json()).code).toBe(0);
  await expectDrawerClosed();
  expect((await (await api.get(`/scm/customer/detail/${customerId}`)).json()).data.creditLimit).toBe('9999.0000');

  // ------------------------------------------------------------------ 查询
  await page.getByPlaceholder('编码 / 名称 / 联系人 / 电话').fill(prefix + 'C1');
  await button(page, '查询').click();
  await expect(row).toBeVisible();

  // ------------------------------------------------- 深链详情（独立隐藏路由）
  await button(row, prefix + '客户甲').click();
  await expect(page).toHaveURL(new RegExp(`customer-detail.*customerId=${customerId}`));
  await page.reload();
  await expect(page.getByText(prefix + 'C1', { exact: true })).toBeVisible();
  await expect(page.getByText('按时间', { exact: true })).toBeVisible();
  await page.screenshot({ path: '../.runtime/w2-customer-detail.png', fullPage: true });

  // ------------------------------------------------------------------ 删除
  await button(page, '返回客户列表').click();
  row = page.getByRole('row').filter({ hasText: prefix + 'C1' });
  await button(row, '删除').click();
  await page.locator('.ant-popover:visible').getByRole('button', { name: /确.*定/ }).click();
  await expect(row).toHaveCount(0);

  // 客户已删除，客户类型这时才能删（否则后端 40938）。
  await page.goto('/#/customer/customer-type-list');
  const typeRow = page.getByRole('row').filter({ hasText: prefix + 'T' });
  await button(typeRow, '删除').click();
  await page.locator('.ant-popover:visible').getByRole('button', { name: /确.*定/ }).click();
  await expect(typeRow).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('read-only role cannot mutate customers and buttons are hidden', async ({ page }) => {
  const readToken = await login(name + '_read');
  const client = await request.newContext({ baseURL: apiUrl, extraHTTPHeaders: { Authorization: `Bearer ${readToken}` } });
  // 载荷本身合法（customerId 非空、version >= 0），所以 @Valid 会通过，
  // 拦截器才会先抛出权限错误 30005 —— 这样断言的是权限，不是参数校验。
  const result = await (await client.post('/scm/customer/delete', { data: { customerId: 1, version: 0 } })).json();
  expect(result.code).toBe(30005);
  await authenticate(page, readToken);
  await page.goto('/#/customer/customer-list');
  await expect(button(page, '查询')).toBeVisible();
  await expect(button(page, '新增客户')).toHaveCount(0);
  await client.get('/login/logout');
  await client.dispose();
});
