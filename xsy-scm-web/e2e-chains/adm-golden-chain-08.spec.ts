/*
 * §15 E2E-08 黄金业务链（清单原文：ONLINE 支付 → Receipt → 自动核销 → 少发产生 overApplied → 售后后历史资金用途不 reverse）
 *
 * 这条链守的是「钱一旦收进来、用途一旦记下来，就只能追加不能改写」：
 *   ONLINE 支付必须**当场**立起一张财务 Receipt，并按实付全额核销到应收；
 *   少发（SHORT）让应收只按实发记，于是核销额 > 净应收 —— overApplied 只能在**读侧**表达，
 *     不允许为了「账面好看」去改历史核销额；
 *   售后退货只**追加红字**，那张收款和那条核销必须原样还在（同一 writeOffId、entryType 仍是 NORMAL、
 *     reverseOfId 仍为空），并且系统来源的收款根本不允许人工冲正（41144），只能走支付退款流程。
 *
 * 契约（读代码确认，别凭直觉）：
 *   `mockScenario` 缺省即 SUCCESS，`/scm/payment/intent/create` **同步**跑完整条财务链（Receipt + 自动核销），
 *   所以这里不需要再投渠道回调；渠道回调只在 DELAYED/EXPIRED 路径上才是必需的。
 *   `FinanceReceiptQueryVO` **没有** sourceType 字段，系统收款只能靠 `method === 'ONLINE_PAYMENT'`
 *   + `externalReference` 前缀 `MOCK-TXN-` + `walletFunding === false` 认出来。
 */
import {expect, test} from '../e2e/scm-test-base';
import {accessibleName, authenticate} from '../e2e/scm-e2e-account';
import {
    call,
    createApprovedReturn,
    createSignedOrder,
    envelope,
    financeDateRange,
    openFinanceHarness,
    type FinanceHarness,
    type Row,
    type SignedOrderFacts,
} from '../e2e/scm-finance-fixtures';

test.describe.configure({mode: 'serial'});

let harness: FinanceHarness;
let facts: SignedOrderFacts;
let receiptsBefore = 0;
let paid = '';
let onlineReceipt: Row;
let writeOffId = 0;

const receiptRows = async () => {
    const range = financeDateRange();
    const rows = await call<Row>(harness.finance, 'post', '/scm/finance/receipt/query',
        {pageNum: 1, pageSize: 100, customerId: Number(facts.customerId),
         startDate: range.startDate, endDate: range.endDate});
    return (rows.list ?? []) as Row[];
};

const writeOffRows = async () => {
    const range = financeDateRange();
    const rows = await call<Row>(harness.finance, 'post', '/scm/finance/write-off/query',
        {pageNum: 1, pageSize: 100, targetId: Number(facts.receivable.receivableId),
         startDate: range.startDate, endDate: range.endDate});
    return (rows.list ?? []) as Row[];
};

const receivableNow = async () => {
    const detail = await call<Row>(harness.finance, 'get',
        `/scm/finance/receivable/${facts.receivable.receivableId}`);
    return (detail.receivable ?? detail) as Row;
};

test.beforeAll(async () => {
    harness = await openFinanceHarness();
});

test.afterAll(async () => {
    if (harness) await harness.close();
});

test('1 少发订单签收：应收只按实发记，必然小于订单结算额', async () => {
    facts = await createSignedOrder(harness, 'GOLDEN08', [
        {quantity: '10.0000', sortedQuantity: '7.0000', sortedResult: 'SHORT'},
    ]);
    const settlement = Number(facts.orderDetail.settlementTotalAmount);
    const actual = Number(facts.receivable.amount);
    expect(actual, '少发后应收应按实发生成').toBeLessThan(settlement);
    expect(actual > 0).toBe(true);
    paid = String(facts.orderDetail.settlementTotalAmount);
    receiptsBefore = (await receiptRows()).length;
});

test('2 ONLINE 支付当场立起系统收款', async () => {
    const intent = await call<Row>(harness.admin, 'post', '/scm/payment/intent/create', {
        customerId: Number(facts.customerId),
        sourceType: 'SALES_ORDER',
        sourceId: Number(facts.orderId),
        amount: paid,
        method: 'ONLINE',
        provider: 'MOCK',
        remark: `${harness.runTag} GOLDEN08 在线支付`,
    });
    expect(String(intent.status), '模拟渠道 SUCCESS 场景应当场支付成功').toBe('SUCCEEDED');
    expect(Number(intent.sourceId)).toBe(Number(facts.orderId));

    const rows = await receiptRows();
    expect(rows.length, '在线支付成功必须留下一张财务 Receipt').toBe(receiptsBefore + 1);
    onlineReceipt = rows.find((row) => String(row.method) === 'ONLINE_PAYMENT') as Row;
    expect(onlineReceipt !== undefined, '没有 method=ONLINE_PAYMENT 的系统收款').toBe(true);
    expect(Number(onlineReceipt.amount)).toBe(Number(paid));
    expect(String(onlineReceipt.externalReference), '收款必须回指渠道交易号').toContain('MOCK-TXN-');
    expect(Boolean(onlineReceipt.walletFunding), '订单收款不是钱包资金').toBe(false);
});

test('3 自动核销把整笔实付挂到应收上，超额只在读侧表达', async () => {
    const rows = await writeOffRows();
    expect(rows.length, '在线支付没有产生自动核销记录').toBe(1);
    const wo = rows[0];
    expect(String(wo.entryType)).toBe('NORMAL');
    expect(String(wo.sourceType)).toBe('RECEIPT');
    expect(Number(wo.sourceId)).toBe(Number(onlineReceipt.receiptId));
    expect(Number(wo.amount)).toBe(Number(paid));
    expect(String(wo.operator)).toContain('SYSTEM:ORDER_FUNDING');
    writeOffId = Number(wo.writeOffId);

    const now = await receivableNow();
    expect(Number(now.writtenOffAmount)).toBe(Number(paid));
    expect(Number(now.netAmount)).toBe(Number(facts.receivable.amount));
    expect(Number(now.openAmount), '整笔已核销，未核销额归零').toBe(0);
    // 少发让实收多于实发：差额必须是「超额核销」，不能被抹平。
    expect(Number(now.overAppliedAmount), 'overApplied 应等于实付减净应收').toBeCloseTo(
        Number(paid) - Number(facts.receivable.amount), 4);
});

test('4 售后退货只追加红字，历史收款与核销原样保留', async () => {
    const redQty = '4.0000';
    const before = await writeOffRows();
    await createApprovedReturn(harness, facts, redQty, 'GOLDEN08');

    const after = await writeOffRows();
    expect(after.length, '售后不应新增反向核销去改写历史资金用途').toBe(before.length);
    const same = after.find((row) => Number(row.writeOffId) === writeOffId) as Row;
    expect(same !== undefined, '原来那条自动核销记录消失了').toBe(true);
    expect(String(same.entryType)).toBe('NORMAL');
    expect(Number(same.amount)).toBe(Number(paid));
    expect(same.reverseOfId ?? null, '历史核销不能被售后反向').toBeNull();

    const receipt = (await receiptRows()).find((row) => Number(row.receiptId) ===
        Number(onlineReceipt.receiptId)) as Row;
    expect(receipt !== undefined, '系统收款在售后后被删掉了').toBe(true);
    expect(Number(receipt.amount)).toBe(Number(paid));

    const now = await receivableNow();
    const detail = await call<Row>(harness.finance, 'get',
        `/scm/finance/receivable/${facts.receivable.receivableId}`);
    expect(((detail.redEntries ?? []) as Row[]).length, '退货应当追加一条红字').toBe(1);
    expect(Number(now.netAmount), '净应收应因红字而下降').toBeLessThan(Number(facts.receivable.amount));
    expect(Number(now.overAppliedAmount), '净应收下降后超额核销应当变大').toBeCloseTo(
        Number(paid) - Number(now.netAmount), 4);
});

test('5 系统收款不允许人工冲正：41144 失败关闭', async () => {
    const denied = await envelope(harness.finance, 'post', '/scm/finance/receipt/reverse', {
        receiptId: Number(onlineReceipt.receiptId), reason: `${harness.runTag} 试图人工冲正系统收款`,
    });
    expect(denied.code, `系统收款应被拒绝人工冲正，实际：${denied.code} ${denied.msg}`).toBe(41144);
    expect(String(denied.msg)).toContain('不能人工冲正');
    const still = (await receiptRows()).find((row) => Number(row.receiptId) ===
        Number(onlineReceipt.receiptId)) as Row;
    expect(still !== undefined, '被拒绝的冲正请求不应留下任何痕迹').toBe(true);
});

test('6 应收列表页体现该超额核销，且整链无浏览器异常', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(`golden08: ${e.message}`));
    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/receivables');
    const row = page.locator('#scm-finance-receivable-table tr')
        .filter({hasText: facts.receivable.receivableNo}).first();
    await expect(row).toContainText('超额核销待处理');
    await row.getByRole('button', {name: accessibleName('明细')}).click();
    const drawer = page.locator('.ant-drawer-open').filter({hasText: '应收明细'}).first();
    await expect(drawer).toContainText('超额核销');
    await page.screenshot({path: '../.runtime/golden08-receivable.png', fullPage: true});
    expect(errors, `浏览器异常：${errors.join(' | ')}`).toEqual([]);
});
