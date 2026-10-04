import {readFileSync} from 'node:fs';
import {test, expect} from './scm-test-base';
import {accessibleName, authenticate} from './scm-e2e-account';
import {call, createSignedOrder, financeDateRange, openFinanceHarness, type FinanceHarness, type Row} from './scm-finance-fixtures';

test.describe.configure({mode: 'serial'});

let harness: FinanceHarness;
test.beforeAll(async () => { harness = await openFinanceHarness(); });
test.afterAll(async () => { if (harness) await harness.close(); });

test('签收按出库实发生成两行应收，并可从页面查看来源和导出 XLSX', async ({page}) => {
    const facts = await createSignedOrder(harness, 'AR', [
        {quantity: '4.0000'},
        {quantity: '3.0000', sortedQuantity: '2.0000', sortedResult: 'SHORT'},
    ]);
    expect(facts.receivable.entryType).toBe('NORMAL');
    expect(facts.receivable.amount).toBe('21.0000');
    expect(facts.receivableDetail.items).toHaveLength(2);
    expect(facts.receivableDetail.items
        .map((item: Row) => [Number(item.quantity), Number(item.amount)] as const)
        .sort((left: readonly [number, number], right: readonly [number, number]) => left[0] - right[0]))
        .toEqual([[2, 7], [4, 14]]);
    expect(facts.receivableDetail.items.every((item: Row) => item.sourceType === 'INVENTORY_OUTBOUND_ITEM')).toBe(true);
    expect(facts.receivableDetail.items.every((item: Row) => Number(item.sourceId) > 0)).toBe(true);

    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/receivables');
    const table = page.locator('#scm-finance-receivable-table');
    await expect(table).toBeVisible();
    const row = table.locator('tr').filter({hasText: facts.receivable.receivableNo}).first();
    await expect(row).toContainText(facts.customerName);
    await expect(row).toContainText('21.0000');
    await row.getByRole('button', {name: accessibleName('明细')}).click();
    const drawer = page.locator('.ant-drawer-open').filter({hasText: '应收明细'}).first();
    await expect(drawer).toBeVisible();
    await expect(drawer).toContainText(facts.orderNo);
    await expect(drawer).toContainText('出库来源');
    await expect(drawer).toContainText('2.0000');
    await page.keyboard.press('Escape');
    await expect(drawer).toHaveCount(0);

    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', {name: accessibleName('导出')}).click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/应收.*\.xlsx$/);
    const file = readFileSync((await download.path())!);
    expect(file.subarray(0, 2).toString()).toBe('PK');

    const latest = await call<Row>(harness.finance, 'post', '/scm/finance/receivable/query', {
        ...financeDateRange(), pageNum: 1, pageSize: 100, customerId: facts.customerId,
    });
    expect((latest.list as Row[]).find((item) => Number(item.receivableId) === Number(facts.receivable.receivableId))?.amount)
        .toBe('21.0000');
});
