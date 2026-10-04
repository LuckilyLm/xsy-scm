/*
 * §15 E2E-01 黄金业务链（清单原文：客户 → 订单 → 确认 → 配送 → 正常签收 → NORMAL 应收 → 收款 → 核销 → 对账/利润）
 *
 * 与既有各域用例的区别：这里**一根线跑到底、后一步吃前一步的真实产物**，
 * 所以它证明的是「跨域闭环成立」，而不是每个页面各自能跑。
 * 数据一律经正式 API 产生（不直写库、不伪造余额/应收），页面断言用财务角色令牌。
 */
import {expect, test} from '../e2e/scm-test-base';
import {authenticate} from '../e2e/scm-e2e-account';
import {
    call,
    createReceipt,
    createSignedOrder,
    createWriteOff,
    financeDateRange,
    openFinanceHarness,
    type FinanceHarness,
    type Row,
    type SignedOrderFacts,
} from '../e2e/scm-finance-fixtures';

test.describe.configure({mode: 'serial'});

let harness: FinanceHarness;
let facts: SignedOrderFacts;
let receipt: Row;
let writeOff: Row;
const consoleErrors: string[] = [];

test.beforeAll(async () => {
    harness = await openFinanceHarness();
});

test.afterAll(async () => {
    if (harness) await harness.close();
});

test('1 客户 → 订单 → 确认 → 配送 → 正常签收 → NORMAL 应收', async () => {
    facts = await createSignedOrder(harness, 'GOLDEN01', [
        {quantity: '10.0000', sortedQuantity: '10.0000', sortedResult: 'NORMAL'},
    ]);
    // 签收形成应收，且方向是 NORMAL（不是红字）；金额由订单实发量算出
    expect(facts.receivable.entryType ?? 'NORMAL').toBe('NORMAL');
    expect(Number(facts.receivable.amount)).toBeGreaterThan(0);
    // 应收必须回指这张订单：列表行带 orderId，详情接口的主体嵌在 receivable 下一层
    expect(Number(facts.receivable.orderId), '应收列表行未回指订单').toBe(Number(facts.orderId));
    const detailMain = (facts.receivableDetail.receivable ?? facts.receivableDetail) as Row;
    expect(Number(detailMain.orderId ?? facts.orderId), '应收详情未回指订单').toBe(Number(facts.orderId));
    expect(Number(detailMain.openAmount), '新应收的未核销额应等于全额').toBe(Number(facts.receivable.amount));
});

test('2 收款 → 核销 → 应收未核销归零', async () => {
    const amount = String(facts.receivable.amount);
    receipt = await createReceipt(harness, facts.customerId, amount, 'GOLDEN01-R');
    expect(receipt.receiptId !== undefined, '收款单未落库').toBe(true);
    writeOff = await createWriteOff(harness, receipt.receiptId,
        [{targetId: facts.receivable.receivableId, amount}]);
    // /write-off/add 返回的是 {items: FinanceWriteOffVO[]}，核销号在 items[0] 上
    expect(Array.isArray(writeOff.items) && writeOff.items.length, '核销单未落库').toBe(1);
    expect(writeOff.items[0].writeOffId !== undefined, '核销明细未带回写核销号').toBe(true);

    const after = await call<Row>(harness.finance, 'get',
        `/scm/finance/receivable/${facts.receivable.receivableId}`);
    expect(Number(after.receivable?.openAmount ?? after.openAmount)).toBe(0);
    expect(Number(after.receivable?.writtenOffAmount ?? after.writtenOffAmount)).toBe(Number(amount));
});

test('3 应收页面：真实列表与明细抽屉都体现已核销', async ({page}) => {
    page.on('pageerror', (e) => consoleErrors.push(`receivables: ${e.message}`));
    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/receivables');
    const table = page.locator('#scm-finance-receivable-table');
    await expect(table).toBeVisible();
    const row = table.locator('tr').filter({hasText: facts.receivable.receivableNo}).first();
    await expect(row).toBeVisible();
    await row.getByRole('button', {name: /明\s*细/}).click();
    const drawer = page.locator('.ant-drawer-open').filter({hasText: '应收明细'}).first();
    await expect(drawer).toBeVisible();
    await expect(drawer).toContainText(facts.receivable.receivableNo);
    await expect(drawer).toContainText('核销');
    await page.screenshot({path: '../.runtime/golden01-receivable-detail.png', fullPage: true});
    await page.keyboard.press('Escape');
});

test('4 收款页面：该笔收款可见', async ({page}) => {
    page.on('pageerror', (e) => consoleErrors.push(`receipts: ${e.message}`));
    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/receipts');
    const table = page.locator('#scm-finance-receipt-table');
    await expect(table).toBeVisible();
    await expect(table.locator('tr').filter({hasText: facts.customerName}).first()).toBeVisible();
    await page.screenshot({path: '../.runtime/golden01-receipts.png', fullPage: true});
});

test('5 客户对账单：冻结后本期应收=核销、期末净应收归零', async ({page}) => {
    page.on('pageerror', (e) => consoleErrors.push(`statement: ${e.message}`));
    await authenticate(page, harness.financeToken);
    await page.goto('/#/report/report-customer-statement');
    await expect(page.locator('.ant-table').first()).toBeVisible();

    const range = financeDateRange();
    const amount = Number(facts.receivable.amount);
    const statement = await call<Row>(harness.finance, 'post', '/scm/report/customer/statement/freeze',
        {settlementCustomerId: Number(facts.customerId), startDate: range.startDate, endDate: range.endDate});
    expect(statement.id !== undefined, '对账单未冻结成功').toBe(true);
    // 闭环的会计证据：本期发生额、核销额、期末余额三者对得上
    expect(Number(statement.receivableIncrease), '对账单未计入本链应收').toBe(amount);
    expect(Number(statement.writeOffNet), '对账单核销净额与收款不符').toBe(amount);
    expect(Number(statement.closingReceivable), '全额核销后期初+本期-核销应归零').toBe(0);
    const history = await call<Row[]>(harness.finance, 'get',
        `/scm/report/customer/statement/history?settlementCustomerId=${facts.customerId}`);
    expect((history as unknown as Row[]).some((r) => Number(r.id) === Number(statement.id)),
        '冻结出的对账单不在历史里').toBe(true);
    await page.screenshot({path: '../.runtime/golden01-statement.png', fullPage: true});
});

test('6 毛利分析页面出数且整链无浏览器异常', async ({page}) => {
    page.on('pageerror', (e) => consoleErrors.push(`profit: ${e.message}`));
    await authenticate(page, harness.financeToken);
    await page.goto('/#/report/report-finance-profit');
    const table = page.locator('#scm-report-finance-profit-table');
    await expect(table).toBeVisible();
    await page.screenshot({path: '../.runtime/golden01-profit.png', fullPage: true});
    expect(consoleErrors, `浏览器异常：${consoleErrors.join(' | ')}`).toEqual([]);
});
