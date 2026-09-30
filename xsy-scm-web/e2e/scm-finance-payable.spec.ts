import {test, expect} from './scm-test-base';
import {accessibleName, authenticate} from './scm-e2e-account';
import {call, createPurchaseReceipt, envelope, openFinanceHarness, type FinanceHarness, type Row} from './scm-finance-fixtures';

test.describe.configure({mode: 'serial'});

let harness: FinanceHarness;
test.beforeAll(async () => { harness = await openFinanceHarness(); });
test.afterAll(async () => { if (harness) await harness.close(); });

test('超收按确认量生成应付，少收关单只保留实收并拒绝超额红字', async ({page}) => {
    const over = await createPurchaseReceipt(harness, 'APOVER', '10.0000', '11.0000');
    const short = await createPurchaseReceipt(harness, 'APSHORT', '10.0000', '4.0000', true);
    expect(over.payables).toHaveLength(1);
    expect(over.payables[0].amount).toBe('68.2000');
    expect(short.payables).toHaveLength(1);
    expect(short.payables[0].amount).toBe('24.8000');

    const overDetail = await call<Row>(harness.finance, 'get', `/scm/finance/payable/${over.payables[0].payableId}`);
    expect(overDetail.items).toHaveLength(1);
    expect(overDetail.items[0].quantity).toBe('11.0000');
    expect(overDetail.items[0].amount).toBe('68.2000');
    const closeDetail = await call<Row>(harness.admin, 'get', `/scm/purchase/detail/${short.purchaseOrder.id}`);
    expect(closeDetail.status).toBe('SHORT_CLOSED');

    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/payables');
    const table = page.locator('#scm-finance-payable-table');
    await expect(table).toBeVisible();
    const row = table.locator('tr').filter({hasText: over.payables[0].payableNo}).first();
    await expect(row).toContainText('68.2000');
    await row.getByRole('button', {name: accessibleName('明细')}).click();
    const detail = page.locator('.ant-drawer-open').filter({hasText: '应付明细'}).first();
    await expect(detail).toContainText(over.payables[0].payableNo);
    await expect(detail).toContainText('11.0000');
    await page.keyboard.press('Escape');

    await row.getByRole('button', {name: accessibleName('登记红字')}).click();
    const redDrawer = page.locator('.ant-drawer-open').filter({hasText: '登记红字应付'}).first();
    await expect(redDrawer).toContainText(over.payables[0].payableNo);
    await expect(redDrawer).toContainText('68.2000');
    const rejected = await envelope(harness.finance, 'post', '/scm/finance/payable/red', {
        originalPayableId: Number(over.payables[0].payableId),
        reason: `${harness.runTag} 超过原应付额度`,
        items: [{
            purchaseOrderItemId: Number(overDetail.items[0].purchaseOrderItemId),
            quantity: '12.0000',
            unitPrice: '6.2000',
            amount: '74.4000',
        }],
    });
    expect(rejected.code).toBe(41137);

    const after = await call<Row>(harness.finance, 'get', `/scm/finance/payable/${over.payables[0].payableId}`);
    expect(after.redEntries).toHaveLength(0);
});
