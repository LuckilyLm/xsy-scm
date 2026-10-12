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
  const request = read('../src/lib/smart-request.js');
  const mock = read('../mock/routes/order.js');
  assert.match(request, /dispatchMock\(url, method, data, \{ header \}\)/);
  assert.match(mock, /Idempotency-Key/);
  // 提交必须回传服务端下发的指纹，并在提交前按备注等最新内容同步幂等键
  assert.match(page, /priceFingerprint/);
  assert.match(page, /async function submit\(\)[\s\S]*syncIdempotencyKey\(\);[\s\S]*mallOrderApi\.submit/);
  // 变价必须让客户重新确认，不能静默重试
  assert.match(page, /40985/);
  // 客户端不提交 customerId，也不自行改价
  assert.doesNotMatch(page, /customerId/);
  assert.doesNotMatch(page, /manualPriceOverride: true/);
});

test('order center exposes customer-scoped list and detail pages', () => {
  const api = read('../src/api/mall/order-api.js');
  const pages = read('../src/pages.json');
  const success = read('../src/pages-sub/checkout/success.vue');
  assert.match(api, /getList/);
  assert.match(api, /getDetail/);
  assert.match(pages, /pages\/order\/index/);
  assert.match(pages, /pages-sub\/order/);
  assert.match(success, /pages-sub\/order\/detail/);
  assert.doesNotMatch(success, /下一阶段开放/);
});

test('order center shows the server-derived display status, not a client state machine', () => {
  const page = read('../src/pages/order/index.vue');
  const detail = read('../src/pages-sub/order/detail.vue');
  const util = read('../src/utils/order-display.js');
  // 文案与档位来自服务端下发的 displayStatus
  assert.match(util, /PENDING: \{ text: '待处理'/);
  assert.match(page, /displayStatusText\(order\.displayStatus\)/);
  assert.match(detail, /displayStatusText\(order\.displayStatus\)/);
  // 客户端不得按订单原始状态自行推导阶段
  assert.doesNotMatch(page, /order\.status === 'CONFIRMED'/);
  assert.doesNotMatch(detail, /order\.status === 'CONFIRMED'/);
});

test('cancel and reorder both go through the server', () => {
  const api = read('../src/api/mall/order-api.js');
  const page = read('../src/pages/order/index.vue');
  const detail = read('../src/pages-sub/order/detail.vue');
  const mock = read('../mock/routes/order.js');
  assert.match(api, /cancel: \(orderId, reason, idempotencyKey\)/);
  assert.match(api, /reorder: \(orderId\)/);
  assert.match(page, /mallOrderApi\.cancel\(/);
  assert.match(page, /mallOrderApi\.reorder\(/);
  assert.match(detail, /mallOrderApi\.cancel\(/);
  // 再来一单只传订单号：不复制历史成交价
  assert.doesNotMatch(api, /reorder[\s\S]{0,160}unitPrice/);
  assert.match(mock, /\/scm\/mall\/orders\/:orderId\/cancel/);
  assert.match(mock, /\/scm\/mall\/orders\/:orderId\/reorder/);
});

test('order actions only offer cancel when the server says it is cancelable', () => {
  const page = read('../src/pages/order/index.vue');
  const detail = read('../src/pages-sub/order/detail.vue');
  assert.match(page, /v-if="order\.cancelable"/);
  assert.match(detail, /v-if="order\.cancelable"/);
});

test('order list labels the amount as an estimate and drops stale tab responses', () => {
  const page = read('../src/pages/order/index.vue');
  // orderedTotalAmount 是订货金额，不等于实付；接入真实支付事实前不得写成「实付金额」
  assert.match(page, /预计订单金额/);
  assert.doesNotMatch(page, /实付金额/);
  // 切分类要作废旧请求，否则会把上一分类的结果显示到新分类
  assert.match(page, /let loadSeq = 0/);
  assert.match(page, /if \(seq !== loadSeq\) return;/);
});

test('abnormal fulfillment is never presented as a plain completion', () => {
  const util = read('../src/utils/order-display.js');
  const page = read('../src/pages/order/index.vue');
  const detail = read('../src/pages-sub/order/detail.vue');
  assert.match(util, /isAbnormalFulfillment/);
  assert.match(page, /isAbnormalFulfillment\(order\.fulfillmentStatus\)/);
  assert.match(detail, /isAbnormalFulfillment\(order\.fulfillmentStatus\)/);
});

test('checkout blocks submission until the current preview succeeds', () => {
  const page = read('../src/pages-sub/checkout/index.vue');
  // 重新预览先作废旧结果：失败时不能继续沿用上一次的金额与指纹
  assert.match(page, /const previewReady = ref\(false\)/);
  assert.match(page, /previewReady\.value = false/);
  assert.match(page, /previewReady\.value = !!fingerprint\.value/);
  // 提交守卫必须同时看「本次预览成功」和「有指纹」
  assert.match(page, /!previewReady\.value \|\| !fingerprint\.value/);
});

test('delivery date uses the Shanghai calendar instead of UTC', () => {
  const page = read('../src/pages-sub/checkout/index.vue');
  const util = read('../src/utils/mall-date.js');
  // toISOString() 取 UTC 日期，中国时间凌晨会得到前一天
  assert.doesNotMatch(page, /toISOString\(\)\.slice\(0, 10\)/);
  assert.match(page, /shanghaiDate\(\)/);
  assert.match(util, /SHANGHAI_OFFSET_MINUTES/);
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
  for (const page of [
    '../src/pages/home/index.vue',
    '../src/pages/category/index.vue',
    '../src/pages-sub/product/search.vue',
    '../src/pages-sub/product/detail.vue',
  ]) {
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
