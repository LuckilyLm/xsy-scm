import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');

test('SmartAdmin success contract uses code zero', () => {
  const constants = read('../src/constants/error-code-const.js');
  const request = read('../src/lib/smart-request.js');
  assert.match(constants, /SUCCESS_CODE = 0/);
  assert.match(request, /code === SUCCESS_CODE && res\?\.ok !== false/);
});

test('release only exposes authentication, catalog, cart, address and order APIs', () => {
  const auth = read('../src/api/mall/auth-api.js');
  const api = read('../src/api/mall/index.js');
  assert.match(auth, /auth\/login/);
  assert.match(auth, /password\/change/);
  assert.doesNotMatch(auth, /sms|wechat/i);
  assert.match(api, /Cart/);
  assert.match(api, /Address/);
  assert.match(api, /Order/);
  assert.doesNotMatch(api, /Account|Home/);
});

test('checkout submits a price fingerprint and an idempotency key', () => {
  const api = read('../src/api/mall/order-api.js');
  const page = read('../src/pages-sub/checkout/index.vue');
  assert.match(api, /\/scm\/mall\/orders\/preview/);
  assert.match(api, /idempotencyKey/);
  // 提交必须回传服务端下发的指纹
  assert.match(page, /priceFingerprint/);
  // 变价必须让客户重新确认，不能静默重试
  assert.match(page, /40985/);
  // 客户端不提交 customerId，也不自行改价
  assert.doesNotMatch(page, /customerId/);
  assert.doesNotMatch(page, /manualPriceOverride: true/);
});

test('address form posts the field names the backend expects', () => {
  const api = read('../src/api/mall/address-api.js');
  const edit = read('../src/pages-sub/address/edit.vue');
  assert.match(api, /\/scm\/mall\/addresses/);
  assert.match(api, /setDefault/);
  assert.match(edit, /receiverName/);
  assert.match(edit, /receiverPhone/);
  assert.match(edit, /detailAddress/);
  // 归属只由服务端从登录身份派生，客户端不提交 customerId
  assert.doesNotMatch(edit, /customerId/);
});

test('login and password forms match the backend payload', () => {
  const store = read('../src/store/modules/system/user.js');
  const password = read('../src/pages/account/password.vue');
  assert.match(store, /const profile = data\.profile \|\| data/);
  assert.match(store, /profile\.loginPhone/);
  assert.match(store, /profile\.firstPasswordChange/);
  assert.match(password, /oldPassword/);
  assert.doesNotMatch(password, /currentPassword/);
});

test('product pages add to the server cart through the shared composable', () => {
  for (const page of ['../src/pages/home/index.vue', '../src/pages/category/index.vue', '../src/pages-sub/product/search.vue', '../src/pages-sub/product/detail.vue']) {
    const source = read(page);
    assert.match(source, /useMallCart/);
    assert.doesNotMatch(source, /购物车.*下一阶段开放|购物车暂未开放/);
  }
});

test('cart quantities travel as strings, never summed on the client', () => {
  const api = read('../src/api/mall/cart-api.js');
  const page = read('../src/pages/cart/index.vue');
  const composable = read('../src/composables/use-mall-cart.js');
  assert.match(api, /updateItem: \(skuId, quantity\)/);
  assert.match(composable, /String\(quantity\)/);
  // 数量以字符串下发，禁止客户端做金额求和
  assert.doesNotMatch(page, /Number\(item\.unitPrice\)/);
});

test('checked-cart total comes from the server estimate endpoint', () => {
  const api = read('../src/api/mall/cart-api.js');
  const page = read('../src/pages/cart/index.vue');
  const mock = read('../mock/routes/cart.js');
  assert.match(api, /\/scm\/mall\/cart\/estimate/);
  assert.match(page, /mallCartApi\.estimate/);
  // 底部合计不得直接用整车金额，也不得用浮点数累加
  assert.doesNotMatch(page, /estimatedAmount\.value = cart/);
  assert.doesNotMatch(page, /reduce\(.*Number\(item\.lineAmount\)/);
  assert.match(mock, /\/scm\/mall\/cart\/estimate/);
});

test('cart total never shows a stale amount while estimating or after failure', () => {
  const page = read('../src/pages/cart/index.vue');
  assert.match(page, /estimateState\.value = 'loading'/);
  assert.match(page, /estimateState\.value = 'error'/);
  assert.match(page, /金额获取失败/);
  assert.match(page, /计算中/);
});

test('production mock alias points at noop implementation', () => {
  const vite = read('../vite.config.js');
  assert.match(vite, /useMock \? 'mock\/index\.js' : 'mock\/noop\.js'/);
});

test('frequent products follow the backend array contract', () => {
  const api = read('../src/api/mall/catalog-api.js');
  const home = read('../src/pages/home/index.vue');
  const mock = read('../mock/routes/catalog.js');
  // 后端 /scm/mall/catalog/frequent 返回 MallProductVO 数组，参数是 days / limit
  assert.match(api, /\/scm\/mall\/catalog\/frequent/);
  assert.doesNotMatch(home, /getFavorites\(\{\s*pageNum/);
  assert.match(home, /getFavorites\(\{\s*days/);
  assert.doesNotMatch(home, /response\.data\?\.list/);
  // mock 与后端一致：返回数组而不是分页包装
  assert.match(mock, /MOCK_PRODUCTS\.filter\(\(p\) => p\.favorite\)\.slice\(0, limit\)/);
});

test('catalog adapter maps backend VO fields to the display model', () => {
  const adapter = read('../src/api/mall/product-adapter.js');
  const mock = read('../mock/fixtures.js');
  // 后端领域字段必须被适配层消费
  assert.match(adapter, /raw\.unitPrice/);
  assert.match(adapter, /raw\.primaryImageUrl/);
  assert.match(adapter, /raw\.specName/);
  assert.match(adapter, /raw\.saleUnit/);
  // mock 必须与后端同构，否则适配层在 mock 下走不通
  assert.match(mock, /unitPrice: price/);
  assert.match(mock, /specName: spec/);
  assert.match(mock, /saleUnit: unit/);
});
