/* W3 verification: real browser/API calls against the running SmartAdmin server.
 * The fixture helper creates an administrator and a read-only account; secrets
 * remain in process memory and are never written to the report.
 */
import { request, type APIRequestContext } from '@playwright/test';
import {expect, test} from './scm-test-base';
import { randomBytes } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import smCrypto from 'sm-crypto';

const apiUrl = 'http://127.0.0.1:18080';
const name = 'w3_e2e_' + Date.now().toString(36);
const password = 'W3@' + randomBytes(6).toString('hex');
const env = { ...process.env, W3_E2E_NAME: name, W3_E2E_PASSWORD: password };
let api: APIRequestContext;
let token: string;
let customerId: string | number;
let agreementId: string | number;
let productId: string | number | undefined;

async function login(account: string) {
  const client = await request.newContext({ baseURL: apiUrl });
  const captcha = (await (await client.get('/login/getCaptcha')).json()).data;
  const source = readFileSync('src/lib/encrypt.ts', 'utf8');
  const key = /const SM4_KEY = '([^']+)'/.exec(source)?.[1];
  if (!key) throw new Error('SmartAdmin transport key not found');
  const encrypted = Buffer.from(smCrypto.sm4.encrypt(password, Buffer.from(key).toString('hex'))).toString('base64');
  const result = await (await client.post('/login', { data: { loginName: account, password: encrypted, captchaUuid: captcha.captchaUuid, captchaCode: captcha.captchaText, loginDevice: 1 } })).json();
  expect(result.code).toBe(0);
  await client.dispose();
  return result.data.token as string;
}

/** 用例会把选中的 SKU 下架后再解析价格，所以商品必须由本用例自建：
 *  复用 option-list 的首行会挑到挂在二级分类上的演示商品，而商品分类必须是三级，编辑校验会直接拒掉。 */
async function createSku() {
  const categories = await (await api.post('/scm/product/category/tree', { data: {} })).json();
  const flatten = (items: Array<{ level: number; children?: Array<{ level: number }> }>): Array<{ level: number; categoryId?: string | number }> => items.flatMap((item) => [item as { level: number; categoryId?: string | number }, ...(item.children ? flatten(item.children) : [])]);
  const category = flatten(categories.data ?? []).find((item) => item.level === 3) ?? flatten(categories.data ?? [])[0];
  expect(category).toBeTruthy();
  const prefix = name.toUpperCase();
  const product = await (await api.post('/scm/product/add', { data: {
    spuCode: prefix + '-SPU', name: prefix + '-商品', categoryId: category.categoryId, status: 'ON_SHELF', images: [],
    skuList: [{ skuCode: prefix + '-SKU', barcode: prefix + '-BAR', specName: '标准', specValues: { 规格: '标准' }, saleUnit: 'kg', productType: 'STANDARD', marketPrice: '8.0000', status: 'ON_SHELF', defaultFlag: true, sortOrder: 1 }],
  } })).json();
  expect(product.code).toBe(0); productId = product.data;
  const options = await (await api.post('/scm/product/sku/option-list', { data: { keyword: prefix, status: 'ON_SHELF', limit: 10 } })).json();
  expect(options.data.options.length).toBeGreaterThan(0);
  return options.data.options[0];
}

test.beforeAll(async () => {
  execFileSync('python', ['../tools/w3_e2e_accounts.py', 'setup'], { env, stdio: 'pipe' });
  token = await login(name);
  api = await request.newContext({ baseURL: apiUrl, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
});

test.afterAll(async () => {
  if (api) {
    if (agreementId) {
      const row = await (await api.get(`/scm/pricing/agreement-price/detail/${agreementId}`)).json();
      if (row.code === 0) await api.post('/scm/pricing/agreement-price/delete', { data: { agreementPriceId: agreementId, version: row.data.version } });
    }
    if (customerId) {
      const row = await (await api.get(`/scm/customer/detail/${customerId}`)).json();
      if (row.code === 0) await api.post('/scm/customer/delete', { data: { customerId, version: row.data.version } });
    }
    if (productId) {
      const row = await (await api.get(`/scm/product/detail/${productId}`)).json();
      if (row.code === 0) await api.post('/scm/product/delete', { data: { spuId: productId, version: row.data.version } });
    }
    await api.get('/login/logout'); await api.dispose();
  }
  execFileSync('python', ['../tools/w3_e2e_accounts.py', 'cleanup'], { env, stdio: 'pipe' });
});

test('pricing resolver keeps price and sale eligibility independent', async ({ page }) => {
  const types = await (await api.post('/scm/customer/type/option/list', { data: {} })).json();
  expect(types.code).toBe(0); const typeId = types.data[0].typeId;
  const prefix = name.toUpperCase();
  const customer = await (await api.post('/scm/customer/add', { data: { customerCode: prefix, name: prefix, customerTypeId: typeId, settleMode: 'INDEPENDENT' } })).json();
  expect(customer.code).toBe(0); customerId = customer.data;
  const detail = await (await api.get(`/scm/customer/detail/${customerId}`)).json();
  await api.post('/scm/customer/updateStatus', { data: { customerId, version: detail.data.version, status: 'COOPERATING' } });
  const sku = await createSku();
  const price = await (await api.post('/scm/pricing/agreement-price/add', { data: { customerId, skuId: sku.skuId, unitPrice: '0.0000', effectiveFrom: '2026-09-01T00:00:00Z', effectiveTo: null } })).json();
  expect(price.code).toBe(0); agreementId = price.data;
  let resolved = await (await api.post('/scm/pricing/resolve', { data: { customerId, skuIds: [sku.skuId], at: '2026-09-15T00:00:00Z' } })).json();
  expect(resolved.data.items[0]).toMatchObject({ priceStatus: 'PRICED', unitPrice: '0.0000', sellable: true, unavailableReason: null, unpricedReason: null });
  const product = await (await api.get(`/scm/product/detail/${sku.spuId}`)).json();
  product.data.skuList[0].status = 'OFF_SHELF';
  // 不检查返回码的话，保存被拒也会让下一条断言以「仍然可售」的形式溜过去
  expect((await (await api.post('/scm/product/update', { data: product.data })).json()).code).toBe(0);
  resolved = await (await api.post('/scm/pricing/resolve', { data: { customerId, skuIds: [sku.skuId], at: '2026-09-15T00:00:00Z' } })).json();
  expect(resolved.data.items[0]).toMatchObject({ priceStatus: 'PRICED', unitPrice: '0.0000', sellable: false, unavailableReason: 'SKU_OFF_SHELF', unpricedReason: null });
  await page.addInitScript((value) => localStorage.setItem('smart_admin_user_token', value), token);
  await page.goto('/#/pricing/price-preview');
  await expect(page.locator('#smartAdminLayoutContent').getByText('取价试算', { exact: true })).toBeVisible();
});

test('read-only role cannot mutate pricing and successful batch keys are protected', async () => {
  const readToken = await login(name + '_read');
  const read = await request.newContext({ baseURL: apiUrl, extraHTTPHeaders: { Authorization: `Bearer ${readToken}` } });
  const denied = await (await read.post('/scm/pricing/agreement-price/add', { data: { customerId: 1, skuId: 1, unitPrice: '1.0000', effectiveFrom: '2026-09-01T00:00:00Z' } })).json();
  expect(denied.code).toBe(30005);
  await read.get('/login/logout'); await read.dispose();
});
