/*
 * §15 E2E-02 黄金业务链（清单原文：集团子客户 → 集团结算 → 收款 → 核销 → 客户对账）
 *
 * ADM-03 的核心不变量是「**下单的人**和**结账的人**可以不是同一个主体」：
 * 子客户自己下单，但应收必须记在集团结算主体名下，收款也只能由该主体交，
 * 最后对账单要按结算主体出，而不是按下单的子客户出。
 * 所以这条链每一步都同时盯「下单客户」与「结算客户」两个字段，
 * 只验金额会把「记错主体」这种最危险的错放过去。
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
let parentCustomerId = '';
let childCustomerId = '';
let facts: SignedOrderFacts;
let receipt: Row;

/** 建一个客户并立刻置为合作中（`createLocatedCustomer` 的集团版：可指定结算主体）。 */
const addCustomer = async (code: string, name: string, settleMode: string,
    settlementCustomerId?: number) => {
    const typeData = await call<Row>(harness.admin, 'post', '/scm/customer/type/option/list', {});
    const typeList = (Array.isArray(typeData) ? typeData : ((typeData as any).options ?? [])) as Row[];
    expect(typeList.length, '库里没有客户类型，无法建集团夹具').toBeGreaterThan(0);
    // CustomerValidator.validateParent 要求「上级客户的类型 typeCode 必须是 GROUP」，
    // 母客户挑错类型就会让子客户建不出来（40032 上级客户不正确）。
    const wantGroup = settleMode === 'GROUP' && !settlementCustomerId;
    const groupOption = typeList.find((t) => String(t.typeCode).toUpperCase() === 'GROUP'
        || String(t.name ?? t.typeName ?? '').includes('集团'));
    if (wantGroup) expect(groupOption !== undefined, '库里没有 GROUP 客户类型，建不出集团母客户').toBe(true);
    const chosen = wantGroup ? groupOption : typeList[0];
    const id = Number(await call(harness.admin, 'post', '/scm/customer/add', {
        customerCode: code.toUpperCase(), name,
        customerTypeId: Number(chosen.typeId ?? chosen.customerTypeId ?? chosen.id),
        settleMode,
        ...(settlementCustomerId ? {parentCustomerId: settlementCustomerId, settlementCustomerId} : {}),
        contactName: '集团夹具联系人', contactPhone: '13800000000',
        address: `${harness.runTag} 集团验收路 1 号`,
    }));
    const created = await call<Row>(harness.admin, 'get', `/scm/customer/detail/${id}`);
    await call(harness.admin, 'post', '/scm/customer/updateStatus',
        {customerId: id, version: created.version, status: 'COOPERATING'});
    return id;
};

test.beforeAll(async () => {
    harness = await openFinanceHarness();
});

test.afterAll(async () => {
    if (harness) await harness.close();
});

test('1 集团母客户 + 集团结算的子客户', async () => {
    parentCustomerId = String(await addCustomer(`${harness.runTag}-GRP`, `${harness.runTag}集团母公司`, 'GROUP'));
    childCustomerId = String(await addCustomer(`${harness.runTag}-SUB`, `${harness.runTag}集团子公司`,
        'GROUP', Number(parentCustomerId)));

    const child = await call<Row>(harness.admin, 'get', `/scm/customer/detail/${childCustomerId}`);
    expect(String(child.settleMode)).toBe('GROUP');
    expect(Number(child.settlementCustomerId), '子客户没有挂上集团结算主体').toBe(Number(parentCustomerId));
});

test('2 子客户下单 → 应收记在集团结算主体名下', async () => {
    facts = await createSignedOrder(harness, 'GOLDEN02', [
        {quantity: '8.0000', sortedQuantity: '8.0000', sortedResult: 'NORMAL'},
    ], childCustomerId);

    expect(Number(facts.receivable.amount) > 0).toBe(true);
    const detail = await call<Row>(harness.finance, 'get',
        `/scm/finance/receivable/${facts.receivable.receivableId}`);
    const main = (detail.receivable ?? detail) as Row;
    // 下单主体是子公司，结账主体必须是母公司 —— 记错主体就等于集团代付失效
    expect(Number(main.orderId), '应收未回指子客户的订单').toBe(Number(facts.orderId));
    expect(Number(main.settlementCustomerId ?? facts.customerId),
        '集团子客户的应收没有记到集团结算主体').toBe(Number(parentCustomerId));
});

test('3 集团统一收款 → 以该收款核销子客户形成的应收', async () => {
    const amount = String(facts.receivable.amount);
    receipt = await createReceipt(harness, parentCustomerId, amount, 'GOLDEN02-R');
    expect(receipt.receiptId !== undefined, '集团收款单未落库').toBe(true);

    const writeOff = await createWriteOff(harness, receipt.receiptId,
        [{targetId: facts.receivable.receivableId, amount}]);
    expect((writeOff.items as Row[]).length, '集团收款未能核销子客户应收').toBe(1);

    const after = await call<Row>(harness.finance, 'get',
        `/scm/finance/receivable/${facts.receivable.receivableId}`);
    expect(Number((after.receivable ?? after).openAmount), '集团收款核销后应收未归零').toBe(0);
});

test('4 对账单按集团结算主体出，且整链无浏览器异常', async ({page}) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const range = financeDateRange();
    const statement = await call<Row>(harness.finance, 'post', '/scm/report/customer/statement/freeze',
        {settlementCustomerId: Number(parentCustomerId), startDate: range.startDate, endDate: range.endDate});
    expect(statement.id !== undefined, '集团对账单未能冻结').toBe(true);
    expect(Number(statement.receivableIncrease), '集团对账单未计入子客户形成的应收').toBe(
        Number(facts.receivable.amount));
    expect(Number(statement.closingReceivable), '集团统一收款核销后期末应归零').toBe(0);

    await authenticate(page, harness.adminToken);
    await page.goto('/#/report/report-customer-statement');
    await expect(page.locator('.ant-table').first()).toBeVisible();
    await page.screenshot({path: '../.runtime/golden02-group-statement.png', fullPage: true});
    expect(errors, `浏览器异常：${errors.join(' | ')}`).toEqual([]);
});
