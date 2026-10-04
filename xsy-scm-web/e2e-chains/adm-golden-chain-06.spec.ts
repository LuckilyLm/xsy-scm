/*
 * §15 E2E-06 黄金业务链（清单原文：满赠 → 冻结赠品 → 分拣/小票 → 发车赠品出库 → 赠品成本 → 利润）
 *
 * 满赠最容易出错的两处：赠品被算进应收（客户没买的东西却挂了账），
 * 以及赠品随发车出库却没有对应的出库流水（库存与成本对不上）。
 * 所以这条链盯三件事：应收不含赠品、赠品有 `PROMOTION_GIFT_OUT` 流水、毛利页能出数。
 *
 * 契约（曾把测试自己的错当成产品缺陷）：
 *   `FULL_GIFT` 的 rule 只允许 `thresholdAmount` / `giftSkuId` / `giftQuantity` 三个键，
 *   多一个键 `PromotionRuleValidator.requireOnly` 就拒收；活动状态取值是 `ACTIVE` / `STOPPED`。
 *   赠品行**不复制成订单行**（`SortingTaskItemVO` 类说明）：它只以赠品权益的身份出现在
 *   分拣任务的合并视图里（`sourceType = PROMOTION_GIFT`），所以夹具按 index 认行会错位（D-41），
 *   现在按 skuId 认。
 */
import {expect, test} from '../e2e/scm-test-base';
import {authenticate} from '../e2e/scm-e2e-account';
import {createSku} from '../e2e/scm-delivery-fixtures';
import {
    call,
    createSignedOrder,
    openFinanceHarness,
    stockSku,
    type FinanceHarness,
    type Row,
    type SignedOrderFacts,
} from '../e2e/scm-finance-fixtures';

test.describe.configure({mode: 'serial'});

const GIFT_QTY = '2.0000';
const THRESHOLD = '10.0000';
const GIFT_STOCK = '30.0000';

let harness: FinanceHarness;
let giftSkuId = 0;
let activityId = 0;
let giftBalanceBefore = '';
let facts: SignedOrderFacts;

const activityRow = (detail: Row): Row => (detail.activity ?? detail) as Row;

const giftBalance = async (): Promise<string> => {
    const rows = await call<Row>(harness.admin, 'post', '/scm/inventory/balance/query', {
        pageNum: 1, pageSize: 100, warehouseId: harness.warehouseId, skuId: giftSkuId,
    });
    return String((rows.list as Row[])?.[0]?.quantity ?? '0.0000');
};

/** 活动是**库级共享状态**：留下 ACTIVE 的满赠，同库其它链的订单就会长出赠品行。 */
const stopActivity = async () => {
    const detail = activityRow(await call<Row>(harness.admin, 'get', `/scm/promotion/activity/${activityId}`));
    if (String(detail.status) === 'STOPPED') return;
    await call(harness.admin, 'post', `/scm/promotion/activity/${activityId}/status`, {
        id: activityId, version: detail.version, status: 'STOPPED',
    });
};

test.beforeAll(async () => {
    harness = await openFinanceHarness();
});

test.afterAll(async () => {
    if (!harness) return;
    // 停不掉就让它变红，不能静默把 ACTIVE 活动留给下一次运行。
    try {
        if (activityId > 0) await stopActivity();
    } finally {
        await harness.close();
    }
});

test('1 赠品备货 + 满赠活动上线', async () => {
    giftSkuId = Number(await createSku(harness.admin, harness.runTag, 'G6G'));
    await stockSku(harness, 'G6S', String(giftSkuId), GIFT_STOCK);
    giftBalanceBefore = await giftBalance();
    expect(Number(giftBalanceBefore), '赠品没备进夹具仓，发车出库必然无货').toBeGreaterThan(Number(GIFT_QTY));

    activityId = Number(await call(harness.admin, 'post', '/scm/promotion/activity/save', {
        activityCode: `${harness.runTag}-A6`,
        activityName: `${harness.runTag} 满赠活动`,
        activityType: 'FULL_GIFT',
        priority: 10,
        validFrom: new Date(Date.now() - 86400000).toISOString(),
        validTo: new Date(Date.now() + 30 * 86400000).toISOString(),
        rule: {thresholdAmount: THRESHOLD, giftSkuId, giftQuantity: GIFT_QTY},
        remark: `${harness.runTag} GOLDEN06`,
    }));
    expect(activityId > 0).toBe(true);

    const created = activityRow(await call<Row>(harness.admin, 'get', `/scm/promotion/activity/${activityId}`));
    if (String(created.status) !== 'ACTIVE') {
        await call(harness.admin, 'post', `/scm/promotion/activity/${activityId}/status`, {
            id: activityId, version: created.version, status: 'ACTIVE',
        });
    }
    const enabled = activityRow(await call<Row>(harness.admin, 'get', `/scm/promotion/activity/${activityId}`));
    expect(String(enabled.status), '满赠活动没能上线').toBe('ACTIVE');
});

test('2 下单即冻结赠品权益，但应收里不含赠品', async () => {
    facts = await createSignedOrder(harness, 'GOLDEN06A', [
        {quantity: '10.0000', sortedQuantity: '10.0000', sortedResult: 'NORMAL'},
    ]);
    const settlement = Number(facts.orderDetail.settlementTotalAmount);
    expect(settlement, `订单额低于满赠门槛 ${THRESHOLD}，本链夹具失效`).toBeGreaterThanOrEqual(Number(THRESHOLD));

    const gifts = (facts.orderDetail.gifts ?? []) as Row[];
    const mine = gifts.filter((gift) => Number(gift.skuId) === giftSkuId);
    expect(mine.length, `满赠未冻结赠品权益，订单里的权益：${JSON.stringify(gifts)}`).toBe(1);
    expect(Number(mine[0].quantity)).toBe(Number(GIFT_QTY));
    expect(Number(mine[0].activityId), '赠品权益没有回指到满赠活动').toBe(activityId);

    // 赠品不复制成订单行，也不进应收：客户没买的东西不能挂账。
    const items = (facts.orderDetail.items ?? []) as Row[];
    expect(items.some((item) => Number(item.skuId) === giftSkuId), '赠品被当成订单行了').toBe(false);
    expect(Number(facts.receivable.amount), '应收应当只有商品结算额').toBe(settlement);
});

test('3 发车产生 PROMOTION_GIFT_OUT 流水并扣减赠品库存', async () => {
    const movements = await call<Row>(harness.admin, 'post', '/scm/inventory/movement/query', {
        pageNum: 1, pageSize: 100, warehouseId: harness.warehouseId, skuId: giftSkuId,
    });
    const out = ((movements.list ?? []) as Row[]).filter((m) => m.movementType === 'PROMOTION_GIFT_OUT');
    expect(out.length, '签收发车后赠品没有 PROMOTION_GIFT_OUT 流水').toBeGreaterThanOrEqual(1);
    const total = out.reduce((sum, m) => sum + Number(m.quantity), 0);
    expect(total, `赠品出库合计 ${total} 应等于冻结的 ${GIFT_QTY}`).toBe(Number(GIFT_QTY));
    // 溯源锚点是赠品权益 id（不是订单行 id），成本按当时的移动加权均价记进流水。
    expect(String(out[0].sourceDocumentType), '赠品出库流水来源类型应指向赠品权益').toBe('ORDER_PROMOTION_GIFT');
    expect(Number(out[0].sourceDocumentItemId) > 0).toBe(true);
    expect(Number(out[0].unitCost), '赠品出库未记成本，毛利会把赠品当成免费货').toBeGreaterThan(0);
    expect(Number(out[0].afterQuantity), '流水里的前后量必须自洽').toBeCloseTo(
        Number(out[0].beforeQuantity) - Number(out[0].quantity), 4);

    const after = await giftBalance();
    expect(Number(after), '赠品出库未扣库存').toBeCloseTo(
        Number(giftBalanceBefore) - Number(GIFT_QTY), 4);
});

test('4 毛利分析页渲染，整链无浏览器异常', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(`golden06: ${e.message}`));
    page.on('console', (message) => {
        if (message.type() === 'error') errors.push(`golden06-console: ${message.text()}`);
    });
    await authenticate(page, harness.adminToken);
    await page.goto('/#/report/report-finance-profit');
    await expect(page.locator('#scm-report-finance-profit-table')).toBeVisible();
    await page.screenshot({path: '../.runtime/golden06-profit.png', fullPage: true});
    expect(errors, `浏览器异常：${errors.join(' | ')}`).toEqual([]);
});
