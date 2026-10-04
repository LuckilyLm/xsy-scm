/*
 * §15 E2E-03 黄金业务链（清单原文：订单 → 退货审批 → 实物接收 → SALES_RETURN_IN → RED → 退款 → 报表）
 *
 * 与 E2E-01 同理：一条 serial 链，后一步吃前一步的真实产物，
 * 并在「退货回库」这一步同时验到库存事实（流水方向 + 余额增加）与财务事实（红字），
 * 因为 ADM-02 最容易出的错就是「退货只改财务、不回库」或「回库了但方向写反」。
 */
import {expect, test} from '../e2e/scm-test-base';
import {authenticate} from '../e2e/scm-e2e-account';
import {
    call,
    createApprovedReturn,
    createSignedOrder,
    financeDateRange,
    openFinanceHarness,
    type FinanceHarness,
    type Row,
    type SignedOrderFacts,
} from '../e2e/scm-finance-fixtures';

test.describe.configure({mode: 'serial'});

let harness: FinanceHarness;
let facts: SignedOrderFacts;
let returnFacts: {returnId: number; refund: Row; approved: Row};
let balanceBefore = '0.0000';
const consoleErrors: string[] = [];
const RETURN_QTY = '4.0000';

test.beforeAll(async () => {
    harness = await openFinanceHarness();
});

test.afterAll(async () => {
    if (harness) await harness.close();
});

test('1 订单 → 签收 → NORMAL 应收（退货前基线）', async () => {
    facts = await createSignedOrder(harness, 'GOLDEN03', [
        {quantity: '10.0000', sortedQuantity: '10.0000', sortedResult: 'NORMAL'},
    ]);
    const skuId = Number(facts.skuIds[0]);
    const before = await call<Row>(harness.admin, 'post', '/scm/inventory/balance/query',
        {pageNum: 1, pageSize: 100, warehouseId: harness.warehouseId, skuId});
    balanceBefore = String((before.list as Row[])?.[0]?.quantity ?? '0.0000');
});

test('2 退货审批 → RED 红字挂在同一张应收上', async () => {
    returnFacts = await createApprovedReturn(harness, facts, RETURN_QTY, 'GOLDEN03');
    expect(returnFacts.approved.status).toBe('APPROVED');
    const detail = await call<Row>(harness.finance, 'get',
        `/scm/finance/receivable/${facts.receivable.receivableId}`);
    const main = (detail.receivable ?? detail) as Row;
    const reds = (detail.redEntries ?? []) as Row[];
    expect(reds.length, '审批后未生成红字').toBe(1);
    expect(Number(main.netAmount) < Number(main.amount), '红字未冲减净应收').toBe(true);
});

test('3 实物接收 → 库存流水方向是 SALES_RETURN_IN 且余额真的增加', async () => {
    const skuId = Number(facts.skuIds[0]);
    const items = ((returnFacts.approved.items ?? []) as Row[]);
    const returnItemId = Number(items[0]?.returnItemId ?? items[0]?.id);
    await call(harness.admin, 'post', '/scm/order/return/receive', {
        returnId: returnFacts.returnId,
        version: returnFacts.approved.version,
        warehouseId: harness.warehouseId,
        items: [{returnItemId, quantity: RETURN_QTY, disposition: 'RETURN_TO_STOCK'}],
    });

    const movements = await call<Row>(harness.admin, 'post', '/scm/inventory/movement/query',
        {pageNum: 1, pageSize: 100, warehouseId: harness.warehouseId, skuId});
    const inbound = (movements.list as Row[]).filter((m) => m.movementType === 'SALES_RETURN_IN');
    expect(inbound.length, '退货未产生 SALES_RETURN_IN 入库流水').toBeGreaterThanOrEqual(1);
    expect(Number(inbound[0].quantity) > 0, '退货入库流水数量必须为正数（方向由类型表达）').toBe(true);

    const after = await call<Row>(harness.admin, 'post', '/scm/inventory/balance/query',
        {pageNum: 1, pageSize: 100, warehouseId: harness.warehouseId, skuId});
    const quantity = Number((after.list as Row[])?.[0]?.quantity ?? 0);
    expect(quantity).toBe(Number(balanceBefore) + Number(RETURN_QTY));
});

test('4 退款登记完成', async () => {
    const completed = await call<Row>(harness.admin, 'post', '/scm/order/refund/complete',
        {refundId: Number(returnFacts.refund.refundId), version: returnFacts.refund.version,
         externalReference: `${harness.runTag}-GOLDEN03-REFUND`});
    expect(['COMPLETED', 'SUCCEEDED'].includes(String(completed.status)),
        `退款登记后状态异常：${completed.status}`).toBe(true);
});

test('5 退货单页面与报表：真实页面体现该退货，且整链无浏览器异常', async ({page}) => {
    page.on('pageerror', (e) => consoleErrors.push(`return-page: ${e.message}`));
    // 退货单列表用管理员令牌看：harness 的 sales 角色**没有**该菜单，深链会落到 404
    // （这正是 scm-data-scope 在证明的 fail-closed 行为，不是页面坏了）。
    // 跨域链里「看页面」用有权令牌，「证权限」另有专门用例，两件事不混在一起。
    await authenticate(page, harness.adminToken);
    await page.goto('/#/order/order-return-list');
    const table = page.locator('#order-return-table');
    await expect(table).toBeVisible();
    await page.screenshot({path: '../.runtime/golden03-return-list.png', fullPage: true});

    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/receivables');
    const receivableTable = page.locator('#scm-finance-receivable-table');
    await expect(receivableTable).toBeVisible();
    const row = receivableTable.locator('tr').filter({hasText: facts.receivable.receivableNo}).first();
    await expect(row).toBeVisible();
    await row.getByRole('button', {name: /明\s*细/}).click();
    const drawer = page.locator('.ant-drawer-open').filter({hasText: '应收明细'}).first();
    await expect(drawer).toContainText('红字');
    await page.screenshot({path: '../.runtime/golden03-receivable-red.png', fullPage: true});

    const range = financeDateRange();
    const statement = await call<Row>(harness.finance, 'post', '/scm/report/customer/statement/freeze',
        {settlementCustomerId: Number(facts.customerId), startDate: range.startDate, endDate: range.endDate});
    expect(Number(statement.receivableRed), '对账单未计入红字冲减').toBeGreaterThan(0);
    expect(consoleErrors, `浏览器异常：${consoleErrors.join(' | ')}`).toEqual([]);
});
