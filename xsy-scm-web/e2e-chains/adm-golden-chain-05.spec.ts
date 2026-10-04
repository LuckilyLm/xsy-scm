/*
 * §15 E2E-05 黄金业务链（清单原文：活动/券 → 发券 → 订单确认冻结优惠 → 配送 → 签收核券 → 净应收 → 利润）
 *
 * ADM-12 这条链的要害是「券什么时候真正生效、生效在哪个数上」：
 *   确认订单时冻结优惠 → 应收必须按**净额**记；
 *   签收时核销券 → 券实例状态必须离开「可用」；
 *   全程不能出现「毛额记应收、净额记利润」这种两边不一致。
 * 所以每一步都同时盯 券实例状态 / 订单毛额 / 应收净额 三处。
 *
 * 契约提醒（曾把测试自己的错当成产品缺陷）：券状态取值是 `ACTIVE` / `STOPPED`，不是 `ENABLED`；
 * `PromotionCouponService.updateStatus` 对不认识的目标状态、以及「要激活但已经是 ACTIVE」都抛 41327，
 * 且 `version` 必须从 detail 带回。
 */
import {expect, test} from '../e2e/scm-test-base';
import {authenticate} from '../e2e/scm-e2e-account';
import {
    call,
    createSignedOrder,
    openFinanceHarness,
    type FinanceHarness,
    type Row,
} from '../e2e/scm-finance-fixtures';

test.describe.configure({mode: 'serial'});

const COUPON_VALUE = '5.0000';

let harness: FinanceHarness;
let couponId = 0;
let instanceId = 0;
let customerId = '';

/** 该端点可能直接回数组，也可能回 {list:[]} / {options:[]}，三种都吃下。 */
const instances = async (): Promise<Row[]> => {
    const rows: any = await call(harness.admin, 'get',
        `/scm/promotion/coupon/instances?customerId=${customerId}`);
    if (Array.isArray(rows)) return rows as Row[];
    return ((rows?.list ?? rows?.options ?? []) as Row[]);
};

const instanceState = async () => (await instances())
    .find((row) => Number(row.instanceId ?? row.id) === instanceId);

test.beforeAll(async () => {
    harness = await openFinanceHarness();
});

test.afterAll(async () => {
    if (harness) await harness.close();
});

test('1 建一张满减券并激活', async () => {
    const validFrom = new Date(Date.now() - 86400000).toISOString();
    const validTo = new Date(Date.now() + 30 * 86400000).toISOString();
    couponId = Number(await call(harness.admin, 'post', '/scm/promotion/coupon/save', {
        couponCode: `${harness.runTag}-C5`, couponName: `${harness.runTag} 满减券`,
        discountType: 'AMOUNT', discountValue: COUPON_VALUE, minOrderAmount: '10.0000',
        validFrom, validTo, remark: `${harness.runTag} GOLDEN05`,
    }));
    expect(couponId > 0).toBe(true);

    const created = await call<Row>(harness.admin, 'get', `/scm/promotion/coupon/${couponId}`);
    if (String(created.status) !== 'ACTIVE') {
        await call(harness.admin, 'post', `/scm/promotion/coupon/${couponId}/status`,
            {id: couponId, version: created.version, status: 'ACTIVE'});
    }
    const enabled = await call<Row>(harness.admin, 'get', `/scm/promotion/coupon/${couponId}`);
    expect(String(enabled.status)).toBe('ACTIVE');
});

test('2 发券给下单客户，券实例先是可用', async () => {
    const plain = await createSignedOrder(harness, 'GOLDEN05A', [
        {quantity: '10.0000', sortedQuantity: '10.0000', sortedResult: 'NORMAL'},
    ]);
    customerId = String(plain.customerId);
    expect(Number(plain.receivable.amount) > Number(COUPON_VALUE)).toBe(true);

    await call(harness.admin, 'post', '/scm/promotion/coupon/issue',
        {couponId, customerId: Number(customerId), quantity: 1});
    // 实例按 couponId 认，不按 id 字段名猜：Instance VO 的键与外层 VO 不同。
    const mine = (await instances()).filter((row) => Number(row.couponId) === couponId);
    expect(mine.length, '发券后该客户名下查不到这张券的实例').toBe(1);
    instanceId = Number(mine[0].id ?? mine[0].instanceId ?? mine[0].couponInstanceId);
    expect(instanceId > 0, `券实例 id 取不到，实际字段：${Object.keys(mine[0]).join(',')}`).toBe(true);
    expect(String(mine[0].status)).toBe('AVAILABLE');
});

test('3 带券下单：确认冻结优惠，应收按净额记', async () => {
    const withCoupon = await createSignedOrder(harness, 'GOLDEN05B', [
        {quantity: '10.0000', sortedQuantity: '10.0000', sortedResult: 'NORMAL'},
    ], customerId, {couponInstanceId: instanceId});

    // 实测口径：应收的 amount 本身就已经是**扣券后的净额**（35 毛额 - 5 券 = 30），
    // 所以「券生效」要拿订单结算金额与应收金额比，而不是在应收上再减一次券值。
    const settlement = Number(withCoupon.orderDetail.settlementTotalAmount);
    const gross = Number(withCoupon.receivable.amount);
    const detail = await call<Row>(harness.finance, 'get',
        `/scm/finance/receivable/${withCoupon.receivable.receivableId}`);
    const main = (detail.receivable ?? detail) as Row;
    const net = Number(main.netAmount);
    expect(settlement, '订单结算金额应等于商品毛额（券作用在应收侧）').toBeGreaterThan(gross);
    expect(gross, `券没有在应收上生效：结算 ${settlement} / 应收 ${gross}`).toBe(
        settlement - Number(COUPON_VALUE));
    expect(net, '无红字时净额应等于应收本身').toBe(gross);
});

test('4 签收后券被核销：实例不再是 AVAILABLE', async () => {
    const mine = (await instances()).filter((row) => Number(row.couponId) === couponId);
    expect(mine.length).toBe(1);
    expect(String(mine[0].status)).not.toBe('AVAILABLE');
});

test('5 毛利分析页面渲染，整链无浏览器异常', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await authenticate(page, harness.adminToken);
    await page.goto('/#/report/report-finance-profit');
    await expect(page.locator('#scm-report-finance-profit-table')).toBeVisible();
    await page.screenshot({path: '../.runtime/golden05-profit.png', fullPage: true});
    expect(errors, `浏览器异常：${errors.join(' | ')}`).toEqual([]);
});
