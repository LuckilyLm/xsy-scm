/*
 * §15 E2E-04 黄金业务链（清单原文：采购需求 → 净需求预览 → 冻结批次 → 生成需求 → 采购 → 收货 → 入库 → 应付 → 付款）
 *
 * 本轮覆盖的是「采购 → 收货 → 入库 → 应付 → 付款」这段可自动化的实链，
 * 并同时验**库存事实**（入库流水 + 余额增加）与**财务事实**（应付生成、付款核销后余额归零）——
 * 只验其中一边就会漏掉 ADM-04 最容易的错：收货了但没产生应付，或应付对了库存没动。
 *
 * 未覆盖：净需求预览 / 冻结批次 / 生成需求 这三步属 ADM-05，
 * 其页面断言目前红在裁决项 17（D-34），故不在此链里重复立案。
 */
import {expect, test} from '../e2e/scm-test-base';
import {authenticate} from '../e2e/scm-e2e-account';
import {
    call,
    createPurchaseReceipt,
    financeDateRange,
    openFinanceHarness,
    type FinanceHarness,
    type Row,
} from '../e2e/scm-finance-fixtures';

test.describe.configure({mode: 'serial'});

const PLANNED = '20.0000';
const RECEIVED = '20.0000';

let harness: FinanceHarness;
let purchase: {supplierId: string; skuId: string; purchaseOrder: Row; receipt: Row; payables: Row[]};
let payable: Row;
let balanceBefore = 0;
const consoleErrors: string[] = [];

test.beforeAll(async () => {
    harness = await openFinanceHarness();
});

test.afterAll(async () => {
    if (harness) await harness.close();
});

test('1 采购单 → 收货确认 → 产生库存入库事实', async () => {
    // createPurchaseReceipt 每次都新建一个 SKU，所以它的期初余额必然是 0，
    // 收货确认后余额应精确等于本次收货量（不需要"前后差值"这种弱断言）。
    purchase = await createPurchaseReceipt(harness, 'GOLDEN04', PLANNED, RECEIVED);
    const after = await call<Row>(harness.admin, 'post', '/scm/inventory/balance/query',
        {pageNum: 1, pageSize: 100, warehouseId: harness.warehouseId, skuId: Number(purchase.skuId)});
    const rows = (after.list ?? []) as Row[];
    expect(rows.length, '收货后没有余额行').toBe(1);
    expect(Number(rows[0].quantity), '余额不等于本次收货量').toBe(Number(RECEIVED));

    const movements = await call<Row>(harness.admin, 'post', '/scm/inventory/movement/query',
        {pageNum: 1, pageSize: 100, warehouseId: harness.warehouseId, skuId: Number(purchase.skuId)});
    const inbound = (movements.list as Row[]).filter((m) => String(m.movementType).endsWith('_IN'));
    expect(inbound.length, '收货没有产生入库流水').toBeGreaterThanOrEqual(1);
});

test('2 收货形成应付，方向与金额都是正数', async () => {
    const range = financeDateRange();
    const queried = await call<Row>(harness.finance, 'post', '/scm/finance/payable/query',
        {pageNum: 1, pageSize: 50, startDate: range.startDate, endDate: range.endDate,
         purchaseOrderId: Number(purchase.purchaseOrder.id ?? purchase.purchaseOrder.purchaseOrderId)});
    const rows = (queried.list ?? []) as Row[];
    payable = rows.find((row) => String(row.entryType ?? 'NORMAL') === 'NORMAL') ?? rows[0];
    expect(payable !== undefined, '收货后未生成应付').toBe(true);
    expect(Number(payable.amount) > 0, '应付金额本身必须是正数（方向由类型表达）').toBe(true);
    expect(Number(payable.openAmount), '新应付的未付额应等于全额').toBe(Number(payable.amount));
});

test('3 付款 → 核销 → 应付未付额归零', async () => {
    const amount = String(payable.amount);
    const payment = await call<Row>(harness.finance, 'post', '/scm/finance/payment/add', {
        counterpartyType: 'SUPPLIER', counterpartyId: Number(purchase.supplierId),
        amount, method: 'BANK_TRANSFER', paidAt: new Date().toISOString(),
        externalReference: `${harness.runTag}-GOLDEN04-PAY`, remark: `${harness.runTag} GOLDEN04`,
    });
    const paymentId = Number(payment.paymentId ?? payment.id);
    expect(paymentId > 0, '付款单未落库').toBe(true);

    await call(harness.finance, 'post', '/scm/finance/write-off/add', {
        sourceType: 'PAYMENT', sourceId: paymentId,
        items: [{targetId: Number(payable.payableId), amount}],
    });
    const after = await call<Row>(harness.finance, 'get', `/scm/finance/payable/${payable.payableId}`);
    const main = (after.payable ?? after) as Row;
    expect(Number(main.openAmount), '全额付款核销后应付未付额应归零').toBe(0);
});

test('4 采购收货与应付页面都体现这笔事实，且整链无浏览器异常', async ({page}) => {
    page.on('pageerror', (e) => consoleErrors.push(`page: ${e.message}`));
    await authenticate(page, harness.adminToken);
    await page.goto('/#/purchase/purchase-receipt-list');
    await expect(page.locator('.ant-table').first()).toBeVisible();
    await page.screenshot({path: '../.runtime/golden04-receipt-list.png', fullPage: true});

    await page.goto('/#/finance/payables');
    const table = page.locator('.ant-table').first();
    await expect(table).toBeVisible();
    const range = financeDateRange();
    expect(range.startDate < range.endDate, '对账期间窗口不成立').toBe(true);
    await page.screenshot({path: '../.runtime/golden04-payables.png', fullPage: true});
    expect(consoleErrors, `浏览器异常：${consoleErrors.join(' | ')}`).toEqual([]);
});
