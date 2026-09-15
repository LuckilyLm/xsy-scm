import { test, expect, request, type Page, type Locator, type APIRequestContext } from '@playwright/test';
import { randomBytes } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import smCrypto from 'sm-crypto';

const apiUrl = 'http://127.0.0.1:18080';
const name = 'w1_e2e_' + Date.now().toString(36);
const password = 'W1@' + randomBytes(6).toString('hex');
const env = { ...process.env, W1_E2E_NAME: name, W1_E2E_PASSWORD: password };
let api: APIRequestContext;
let token: string;
const productIds: (number | string)[] = [];
const categoryIds: (number | string)[] = [];

// Ant Design inserts a visual space between two CJK characters in button labels
// ("查询" renders as "查 询"). Match the accessible name with optional whitespace
// between characters instead of weakening the assertion to a substring match.
const escapeRe = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const buttonName = (text: string) => new RegExp('^' + [...text].map(escapeRe).join('\\s*') + '$');
const button = (root: Page | Locator, text: string) => root.getByRole('button', { name: buttonName(text) });

async function login(account: string) {
  const client = await request.newContext({ baseURL: apiUrl });
  const captcha = (await (await client.get('/login/getCaptcha')).json()).data;
  const source = readFileSync('src/lib/encrypt.ts', 'utf8');
  const key = /const SM4_KEY = '([^']+)'/.exec(source)?.[1];
  if (!key) throw new Error('SmartAdmin transport key not found');
  const encrypted = Buffer.from(smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
  const result = await (await client.post('/login', { data: { loginName: account, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1 } })).json();
  expect(result.code, 'SmartAdmin login succeeds').toBe(0);
  await client.dispose(); return result.data.token as string;
}
test.beforeAll(async () => {
  execFileSync('python', ['../tools/w1_e2e_accounts.py', 'setup'], { env, stdio: 'pipe' });
  token = await login(name); api = await request.newContext({ baseURL: apiUrl, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
});
test.afterAll(async () => {
  if (api) {
    for (const id of productIds) {
      const detail = await (await api.get(`/scm/product/detail/${id}`)).json();
      if (detail.code === 0) await api.post('/scm/product/delete', { data: { spuId: id, version: detail.data.version } });
    }
    for (const id of [...categoryIds].reverse()) {
      const detail = await (await api.get(`/scm/product/category/${id}`)).json();
      if (detail.code === 0) await api.post('/scm/product/category/delete', { data: { categoryId: id, version: detail.data.version } });
    }
    await api.get('/login/logout'); await api.dispose();
  }
  execFileSync('python', ['../tools/w1_e2e_accounts.py', 'cleanup'], { env, stdio: 'pipe' });
});
async function authenticate(page: Page, value = token) {
  await page.addInitScript(value => localStorage.setItem('smart_admin_user_token', value), value);
}
async function saveCategory(page: Page, code: string, label: string) {
  const modal = page.locator('.ant-modal:visible');
  await modal.getByLabel('分类编码').fill(code); await modal.getByLabel('分类名称').fill(label);
  const response = page.waitForResponse(r => r.url().endsWith('/scm/product/category/add'));
  await modal.getByRole('button', { name: /确.*定/ }).click();
  const body = await (await response).json(); expect(body.code).toBe(0); categoryIds.push(body.data);
  await expect(modal).not.toBeVisible();
}
test('live product pilot: categories, SKU delta, SPU images, search, deep link and deletion', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', e => errors.push(e.message));
  await authenticate(page); await page.goto('/#/product/category-list');
  const prefix = name.toUpperCase();
  await button(page, '新增分类').click(); await saveCategory(page, prefix + '1', prefix + '一级');
  let row = page.getByRole('row').filter({ hasText: prefix + '一级' });
  await button(row, '新增子分类').click(); await saveCategory(page, prefix + '2', prefix + '二级');
  row = page.getByRole('row').filter({ hasText: prefix + '一级' }); await row.locator('.ant-table-row-expand-icon').click();
  row = page.getByRole('row').filter({ hasText: prefix + '二级' });
  await button(row, '新增子分类').click(); await saveCategory(page, prefix + '3', prefix + '三级');
  await page.goto('/#/product/product-list'); await button(page, '新增商品').click();
  // a-drawer keeps its root element mounted after closing (destroy-on-close only drops
  // the body), so "closed" is asserted through the ant-drawer-open state class.
  const drawer = page.locator('.ant-drawer');
  const openDrawer = page.locator('.ant-drawer-open');
  const expectDrawerClosed = () => expect(openDrawer).toHaveCount(0);
  await drawer.getByLabel('商品名称', { exact: true }).fill(prefix + '苹果'); await drawer.getByLabel('SPU 编码', { exact: true }).fill(prefix + 'SPU');
  await drawer.getByLabel('商品分类', { exact: true }).click();
  await page.locator('.ant-select-dropdown:visible').getByText(prefix + '三级', { exact: true }).click();
  await drawer.getByLabel('SKU 1 编码', { exact: true }).fill(prefix + 'A'); await drawer.getByLabel('SKU 1 单位', { exact: true }).fill('kg');
  await button(drawer, '添加属性').click(); await drawer.getByLabel('SKU 1 属性值 1', { exact: true }).fill('大果');
  await button(drawer, '添加 SKU').click();
  await drawer.getByLabel('SKU 2 编码', { exact: true }).fill(prefix + 'B'); await drawer.getByLabel('SKU 2 单位', { exact: true }).fill('kg');
  await drawer.getByLabel('SKU 2 规格名称', { exact: true }).fill('小果'); await drawer.getByLabel('SKU 2 条码', { exact: true }).fill(prefix + 'BAR');
  await drawer.locator('input[type=file]').setInputFiles({ name: 'pilot.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aRZkAAAAASUVORK5CYII=', 'base64') });
  await expect(drawer.getByText('主图', { exact: true })).toBeVisible();
  const created = page.waitForResponse(r => r.url().endsWith('/scm/product/add'));
  await button(drawer, '保存商品').click(); const body = await (await created).json(); expect(body.code).toBe(0); const id = body.data; productIds.push(id);
  await expectDrawerClosed(); row = page.getByRole('row').filter({ hasText: prefix + 'SPU' });
  await expect(row).toBeVisible(); await row.locator('.ant-table-row-expand-icon').click(); await expect(page.getByText(prefix + 'BAR', { exact: true })).toBeVisible();
  const before = (await (await api.get(`/scm/product/detail/${id}`)).json()).data;
  await button(row, '编辑').click(); await expect(drawer.getByLabel('SKU 1 编码', { exact: true })).toHaveValue(prefix + 'A');
  await drawer.getByLabel('SKU 1 市场价', { exact: true }).fill('12.3456'); await drawer.getByLabel('将第 2 个 SKU 设为默认').check();
  await button(drawer, '保存商品').click(); await expectDrawerClosed();
  const after = (await (await api.get(`/scm/product/detail/${id}`)).json()).data;
  expect(after.skuList.map((s: { skuId: string }) => s.skuId)).toEqual(before.skuList.map((s: { skuId: string }) => s.skuId));
  expect(after.defaultSku.skuCode).toBe(prefix + 'B'); expect(after.skuList[0].marketPrice).toBe('12.3456');
  row = page.getByRole('row').filter({ hasText: prefix + 'SPU' }); await button(row, '下架').click();
  await page.locator('.ant-popover:visible').getByRole('button', { name: /确.*定/ }).click();
  await expect(button(row, '上架')).toBeVisible();
  await page.getByPlaceholder('商品名 / 编码 / 条码').fill(prefix + 'BAR'); await button(page, '查询').click();
  await expect(row).toBeVisible(); await button(row, prefix + '苹果').click();
  await expect(page).toHaveURL(new RegExp(`product-detail.*spuId=${id}`)); await page.reload(); await expect(page.getByText(prefix + 'SPU', { exact: true })).toBeVisible();
  const image = page.locator('.ant-image-img').first(); await expect(image).toBeVisible(); await expect.poll(() => image.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0);
  await page.screenshot({ path: '../.runtime/w1-product-detail.png', fullPage: true });
  await button(page, '返回商品列表').click(); row = page.getByRole('row').filter({ hasText: prefix + 'SPU' });
  await button(row, '删除').click(); await page.locator('.ant-popover:visible').getByRole('button', { name: /确.*定/ }).click();
  await expect(row).toHaveCount(0); expect(errors).toEqual([]);
});
test('read-only role cannot mutate products and buttons are hidden', async ({ page }) => {
  const readToken = await login(name + '_read');
  const client = await request.newContext({ baseURL: apiUrl, extraHTTPHeaders: { Authorization: `Bearer ${readToken}` } });
  const result = await (await client.post('/scm/product/delete', { data: { spuId: 1, version: 0 } })).json();
  expect(result.code).toBe(30005);
  await authenticate(page, readToken); await page.goto('/#/product/product-list'); await expect(button(page, '查询')).toBeVisible();
  await expect(button(page, '新增商品')).toHaveCount(0);
  await client.get('/login/logout'); await client.dispose();
});
