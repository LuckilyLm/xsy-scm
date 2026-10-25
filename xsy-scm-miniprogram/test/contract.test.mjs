import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { URL } from 'node:url';

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');

test('SmartAdmin success contract uses code zero', () => {
  const constants = read('../src/constants/error-code-const.js');
  const request = read('../src/lib/smart-request.js');
  assert.match(constants, /SUCCESS_CODE = 0/);
  assert.match(request, /code === SUCCESS_CODE && res\?\.ok !== false/);
});

test('release exposes customer-scoped commerce APIs without employee account endpoints', () => {
  const auth = read('../src/api/mall/auth-api.js');
  const api = read('../src/api/mall/index.js');
  assert.match(auth, /auth\/login/);
  assert.match(auth, /password\/change/);
  assert.doesNotMatch(auth, /sms|wechat/i);
  assert.match(api, /Cart/);
  assert.match(api, /Address/);
  assert.match(api, /Order/);
  assert.match(api, /AfterSale/);
  assert.match(api, /Funds/);
  assert.match(api, /mallHomeApi \} from '\.\/home-api'/);
  assert.doesNotMatch(api, /Account/);
});

test('funds pages use only customer read APIs and do not create payment commands', () => {
  const api = read('../src/api/mall/funds-api.js');
  const pages = read('../src/pages.json');
  const mine = read('../src/pages/mine/index.vue');
  const wallet = read('../src/pages-sub/wallet/movements.vue');
  const credit = read('../src/pages-sub/credit/receivables.vue');
  const mock = read('../mock/routes/funds.js');
  assert.match(api, /\/scm\/mall\/funds\/wallet\/summary/);
  assert.match(api, /\/scm\/mall\/funds\/receivables/);
  assert.doesNotMatch(api, /\/scm\/(balance|finance)\//);
  assert.doesNotMatch(api, /postRequest|recharge|repay/);
  assert.match(pages, /pages-sub\/wallet/);
  assert.match(pages, /pages-sub\/credit/);
  assert.match(mine, /goWallet/);
  assert.match(mine, /goCredit/);
  assert.match(wallet, /row\.returnId/);
  assert.match(credit, /settleState/);
  assert.match(mock, /creditLimitVisible: false/);
});

test('after-sale center exposes apply, list and detail pages', () => {
  const api = read('../src/api/mall/after-sale-api.js');
  const pages = read('../src/pages.json');
  const mine = read('../src/pages/mine/index.vue');
  assert.match(api, /\/scm\/mall\/after-sales/);
  assert.match(api, /getList/);
  assert.match(api, /getDetail/);
  assert.match(api, /apply: \(param, idempotencyKey\)/);
  assert.match(api, /cancel: \(returnId, idempotencyKey\)/);
  assert.match(pages, /pages-sub\/after-sale/);
  assert.match(mine, /pages-sub\/after-sale\/list/);
});

test('after-sale apply submits items and reason with an idempotency key', () => {
  const page = read('../src/pages-sub/after-sale/apply.vue');
  // 幂等键与「订单 + 明细 + 原因」绑定，重复提交不重复建单
  assert.match(page, /const payload = \{ orderId: orderId\.value, reason: reason\.value, items \};/);
  assert.match(page, /mallAfterSaleApi\.apply\(payload, key\)/);
  assert.match(page, /aftersale-\$\{orderId\.value\}/);
  // 预估金额只是展示，核定以服务端审核为准
  assert.match(page, /以平台审核为准/);
  // 归属由服务端按登录身份判定，客户端不提交 customerId
  assert.doesNotMatch(page, /customerId/);
});

test('after-sale estimates use fixed-point math, never float sums', () => {
  const page = read('../src/pages-sub/after-sale/apply.vue');
  const util = read('../src/utils/after-sale-display.js');
  assert.match(page, /estimateLineAmount/);
  assert.match(page, /sumAmounts/);
  // 金额按四位小数定点整数运算
  assert.match(util, /Math\.round\(Number\(quantity\) \* 10000\)/);
  assert.doesNotMatch(util, /Number\(quantity\) \* Number\(unitPrice\)/);
});

test('after-sale status text maps the server-derived display status', () => {
  const list = read('../src/pages-sub/after-sale/list.vue');
  const util = read('../src/utils/after-sale-display.js');
  assert.match(util, /REVIEWING: \{ text: '审核中'/);
  assert.match(list, /afterSaleStatusText\(record\.displayStatus\)/);
  // 客户端不按原始退货状态自行推导阶段
  assert.doesNotMatch(list, /record\.status === 'APPROVED'/);
});

test('after-sale detail renders server progress and the original-funding breakdown', () => {
  const detail = read('../src/pages-sub/after-sale/detail.vue');
  const util = read('../src/utils/after-sale-display.js');
  // 节点文案与 done 都由服务端给出，客户端只渲染
  assert.match(detail, /v-for="\(node, index\) in record\.progress"/);
  assert.match(detail, /:class="\{ 'ad-step--pending': !node\.done \}"/);
  // 原路退回明细：方式与金额来自服务端分摊事实
  assert.match(detail, /record\.refund\.allocations/);
  assert.match(detail, /refundMethodText\(allocation\.method\)/);
  assert.match(detail, /allocationStatusText\(allocation\.status\)/);
  // 内部失败码不透出：失败只显示「退款异常，请联系客服」
  assert.match(util, /FAILED: '退款异常，请联系客服'/);
  assert.doesNotMatch(util, /failureCode|failureMessage/);
});

test('after-sale cancel only fires when the server says it is cancelable', () => {
  const detail = read('../src/pages-sub/after-sale/detail.vue');
  assert.match(detail, /v-if="record\.cancelable"/);
  assert.match(detail, /mallAfterSaleApi\.cancel\(returnId\.value, `aftersale-cancel-\$\{returnId\.value\}`\)/);
});

test('order detail links to the after-sale center', () => {
  const detail = read('../src/pages-sub/order/detail.vue');
  assert.match(detail, /mallAfterSaleApi\.getList/);
  assert.match(detail, /canApplyAfterSale\(order\)/);
  assert.match(detail, /pages-sub\/after-sale\/apply/);
  // 售后加载失败不能拖垮订单详情
  assert.match(detail, /售后状态独立加载/);
});

test('after-sale mock mirrors the backend routes', () => {
  const mock = read('../mock/routes/after-sale.js');
  const index = read('../mock/index.js');
  assert.match(mock, /\/scm\/mall\/after-sales/);
  assert.match(mock, /\/scm\/mall\/after-sales\/:returnId\/cancel/);
  assert.match(mock, /Idempotency-Key/);
  assert.match(index, /afterSaleRoutes/);
  // mock 与后端同构：撤销仅限待审核
  assert.match(mock, /record\.status !== 'PENDING'/);
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

test('checkout renders server-provided settlement options and submits the chosen method', () => {
  const page = read('../src/pages-sub/checkout/index.vue');
  // 可用性判断完全来自服务端；客户端只做默认选择与失效改选，不自行推断
  assert.match(page, /applySettlementOptions\(data\.settlementOptions \|\| \[\]\)/);
  assert.match(page, /settlementOptions\.value = options/);
  // 不可用的结算方式不得被选中
  assert.match(page, /if \(!option\.enabled\) \{/);
  // 结算方式变化要换幂等键，避免被旧键回放成另一笔约定
  assert.match(page, /settlementMethod\.value === option\.method\) return;[\s\S]*syncIdempotencyKey\(\);/);
  // 提交时把选择回传服务端
  assert.match(page, /settlementMethod: settlementMethod\.value \|\| undefined/);
  // 共享结算下服务端不回金额，客户端必须保持沉默，不得用推算值代替
  assert.match(page, /if \(option\.amountVisible !== true\) return '';/);
  assert.doesNotMatch(page, /option\.availableAmount \|\| 0/);
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
  // 标签在 d33dc21e 缩短为「预计金额」；关键意图是不写成「实付金额」
  assert.match(page, /预计金额/);
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

test('checkout refuses to submit when the preview reports unavailable items', () => {
  const page = read('../src/pages-sub/checkout/index.vue');
  // 服务端已把不可售项的金额排除，前端不能只看金额：不可售项必须同时挡住提交
  assert.match(page, /const unavailableItems = ref\(\[\]\)/);
  assert.match(page, /unavailableItems\.value = nextItems\.filter\(\(item\) => !isAvailable\(item\)\)/);
  assert.match(page, /unavailableItems\.value\.length === 0/);
  assert.match(page, /if \(unavailableItems\.value\.length\) \{/);
  assert.match(page, /返回购物车调整/);
});

test('checkout only applies the latest preview response', () => {
  const page = read('../src/pages-sub/checkout/index.vue');
  // 连续预览时旧响应后到会覆盖新金额与指纹，必须用请求序号丢弃
  assert.match(page, /let previewSeq = 0/);
  assert.match(page, /const seq = \+\+previewSeq/);
  assert.match(page, /if \(seq !== previewSeq\) return;/);
  assert.match(page, /if \(seq === previewSeq\) previewing\.value = false/);
});

test('order detail renders the server-provided delivery progress', () => {
  const page = read('../src/pages-sub/order/detail.vue');
  const mock = read('../mock/routes/order.js');
  // 节点文案与 done 都由服务端按正式事实给出，客户端只渲染，不自己推导进度
  assert.match(page, /v-for="\(node, index\) in order\.progress"/);
  assert.match(page, /:class="\{ 'od-step--pending': !node\.done \}"/);
  // 配送详情只读配送事实（线路 / 发车 / 司机），未排车时不编线路
  assert.match(page, /order\.delivery\.routeName/);
  assert.match(page, /order\.delivery\.dispatchedAt/);
  assert.match(page, /后台尚未安排配送线路/);
  // mock 与后端同形：同一套节点
  assert.match(mock, /function progressOf\(order\)/);
  assert.match(mock, /delivery: order\.delivery \|\| null/);
  assert.match(mock, /progress: progressOf\(order\)/);
});

test('customer timeline never exposes internal operations', () => {
  const util = read('../src/utils/order-display.js');
  // 授信放行 / 改价 / 库存预留都是内部审计动作，不得出现在客户可见文案里
  assert.doesNotMatch(util, /CREDIT_OVERRIDE/);
  assert.doesNotMatch(util, /RESERVE_STOCK/);
  assert.doesNotMatch(util, /超额授信/);
  // 兜底也不能把原始枚举名透出去
  assert.match(util, /\|\| '状态更新'/);
});

test('order timestamps are converted to Asia/Shanghai', () => {
  const util = read('../src/utils/order-display.js');
  const date = read('../src/utils/mall-date.js');
  // 后端时间的偏移不保证是 +08:00（PG 会话时区是 UTC 时就是 +00:00），不能直接截字符串
  assert.match(util, /shanghaiDateTime\(value\)/);
  assert.doesNotMatch(util, /replace\('T', ' '\)\.slice\(0, 16\)/);
  assert.match(date, /export function shanghaiDateTime/);
  assert.match(date, /SHANGHAI_OFFSET_MINUTES/);
});

test('abnormal delivery result is labelled as an exception, not a signature', () => {
  const page = read('../src/pages-sub/order/detail.vue');
  // 异常签收也是终态：用「配送结果记录时间」，并把结果显式写成配送异常
  assert.match(page, /配送结果记录时间/);
  assert.match(page, /'配送异常' : '已签收'/);
  assert.match(page, /异常原因/);
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
  // 5a8b821f 把加购数量参数改名 effective；关键意图是数量以字符串下发
  assert.match(composable, /String\(effective\)/);
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

test('published mall home uses the customer endpoint and checks web links against blocks and the active popup', () => {
  const home = read('../src/pages/home/index.vue');
  const api = read('../src/api/mall/home-api.js');
  const web = read('../src/pages-sub/mall/web.vue');
  const routes = read('../src/pages.json');
  assert.match(api, /getRequest\('\/scm\/mall\/home'/);
  assert.match(api, /response\.data\?\.blocks/);
  assert.match(api, /\.map\(adaptProduct\)/);
  assert.match(home, /mallHomeApi\.published\(\)/);
  assert.match(home, /homeLayout\.value\?\.releaseId != null/);
  assert.match(home, /request !== homeRequest \|\| !userStore\.isLogin \|\| userStore\.customerId !== customerId/);
  assert.match(home, /v-if="!hasPublishedLayout"/);
  // 后台可配置的公告与快捷入口必须在首页真正渲染，不能只存在草稿里
  assert.match(home, /section\.type === 'ANNOUNCEMENT'/);
  assert.match(home, /section\.type === 'SHORTCUT_GROUP'/);
  assert.doesNotMatch(home, /section\.type !== 'ANNOUNCEMENT'/);
  // 分类/领券入口跟在轮播之后，不再固定压在轮播上方
  assert.match(home, /section\.type === 'RAIL'/);
  assert.match(home, /bannerIndex \+ 1/);
  assert.match(web, /mallHomeApi\.published\(\)/);
  assert.match(web, /mallHomeApi\.popup\(\)/);
  // 只配置在生效弹窗里的外链也必须能打开，不能只认已发布首页组件
  assert.match(web, /Promise\.allSettled\(\[mallHomeApi\.published\(\), mallHomeApi\.popup\(\)\]\)/);
  assert.match(web, /popup\?\.active \? popup\.items \|\| \[\]/);
  assert.match(web, /item\.linkType === 'EXTERNAL_URL' && item\.linkTarget === value/);
  assert.match(routes, /"root": "pages-sub\/mall"/);
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

test('pay sends no amount: the server owns the deduction figure', () => {
  const api = read('../src/api/mall/order-api.js');
  // 支付只带订单号与幂等键，金额一律不进请求体
  assert.match(api, /pay: \(orderId, idempotencyKey\) => postRequest\(`\/scm\/mall\/orders\/\$\{orderId\}\/pay`, \{\}, \{ idempotencyKey \}\)/);
  assert.doesNotMatch(api, /pay[\s\S]{0,160}(amount|payableAmount)/);
});

test('order detail shows wallet pay only when the server says there is a due amount', () => {
  const page = read('../src/pages-sub/order/detail.vue');
  const util = read('../src/utils/order-display.js');
  assert.match(page, /v-if="canPayByWallet\(order\)"/);
  assert.match(page, /mallOrderApi\.pay\(orderId\.value, `pay-\$\{orderId\.value\}`\)/);
  // 入口条件：服务端下发的结算约定是余额，且下发的应付减已付仍大于零
  assert.match(util, /order\.settlementMethod !== 'WALLET'/);
  assert.match(util, /Math\.round\(Number\(order\.payableAmount\) \* 10000\)/);
  assert.doesNotMatch(util, /Number\(order\.payableAmount\) - Number\(order\.paidAmount\)/);
});

test('funds rows render server amounts and never a client-side guess', () => {
  const page = read('../src/pages-sub/order/detail.vue');
  // 应付/已付/待付整组只在服务端下发 payableAmount 时出现；确认前没有应付口径就不显示
  assert.match(page, /v-if="order\.payableAmount"/);
  assert.match(page, /应付金额（优惠后）/);
  assert.match(page, /settlementMethodText\(order\.settlementMethod\)/);
  assert.doesNotMatch(page, /payableAmount = /);
});

test('mock pay mirrors the backend money rules', () => {
  const mock = read('../mock/routes/order.js');
  assert.match(mock, /\/scm\/mall\/orders\/:orderId\/pay/);
  assert.match(mock, /order\.settlementMethod !== 'WALLET'/);
  assert.match(mock, /Number\(payable\) - Number\(paid\) <= 0/);
  // 结算方式仍是下单必选项，且资金投影与后端同形
  assert.match(mock, /settlementMethod: data\?\.settlementMethod \|\| null/);
  assert.match(mock, /function fundsOf\(order\)/);
});

test('checkout settlement options come from the server preview', () => {
  const page = read('../src/pages-sub/checkout/index.vue');
  const mock = read('../mock/routes/order.js');
  assert.match(page, /applySettlementOptions\(data\.settlementOptions \|\| \[\]\)/);
  assert.doesNotMatch(page, /settlementOptions\.value = \[\{ method/i);
  // mock 也必须返回同形的可选项，否则开发环境走不到支付链路
  assert.match(mock, /settlementOptions: \[/);
});

test('order summary aggregates on the server and reuses the shared quantity formatter', () => {
  const api = read('../src/api/mall/order-api.js');
  const page = read('../src/pages-sub/order/summary.vue');
  const pages = read('../src/pages.json');
  // 汇总必须走服务端聚合：客户端拿分页行自己加，会漏掉没翻到的页
  assert.match(api, /getSummary: \(param\) => getRequest\('\/scm\/mall\/orders\/summary', param\)/);
  assert.match(page, /mallOrderApi\.getSummary\(/);
  // 数量展示复用统一格式化，不在这里另写一套小数截断
  assert.match(page, /displayQuantity\(row\.quantity\)/);
  assert.doesNotMatch(page, /toFixed\(/);
  // 区间按日期传，客户端不拼时间戳
  assert.match(page, /startDate: startDate\.value \|\| undefined/);
  assert.match(pages, /"path": "summary"/);
});
