import {test, expect} from './scm-test-base';
import {accessibleName, authenticate} from './scm-e2e-account';
import {call, createApprovedReturn, createSignedOrder, openFinanceHarness, type FinanceHarness, type Row} from './scm-finance-fixtures';

test.describe.configure({mode: 'serial'});

let harness: FinanceHarness;
test.beforeAll(async () => { harness = await openFinanceHarness(); });
test.afterAll(async () => { if (harness) await harness.close(); });

test('批准累计超过少拣应收的退货，红字全额生成并显示待处理余额', async ({page}) => {
    const facts = await createSignedOrder(harness, 'RED', [
        {quantity: '10.0000', sortedQuantity: '7.0000', sortedResult: 'SHORT'},
    ]);
    expect(facts.receivable.amount).toBe('24.5000');

    const first = await createApprovedReturn(harness, facts, '4.0000', 'RED-A');
    const second = await createApprovedReturn(harness, facts, '4.0000', 'RED-B');
    expect(first.approved.status).toBe('APPROVED');
    expect(second.approved.status).toBe('APPROVED');
    expect(first.refund.refundAmount).toBe('14.0000');
    expect(second.refund.refundAmount).toBe('14.0000');

    const current = await call<Row>(harness.finance, 'get', `/scm/finance/receivable/${facts.receivable.receivableId}`);
    expect(current.receivable.netAmount).toBe('-3.5000');
    expect(current.receivable.openAmount).toBe('0.0000');
    expect(current.receivable.overAppliedAmount).toBe('3.5000');
    expect(current.redEntries).toHaveLength(2);
    expect(current.redEntries.reduce((sum: number, item: Row) => sum + Number(item.amount), 0)).toBe(28);

    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/receivables');
    const row = page.locator('#scm-finance-receivable-table tr')
        .filter({hasText: facts.receivable.receivableNo}).first();
    await expect(row).toContainText('超额核销待处理');
    await row.getByRole('button', {name: accessibleName('明细')}).click();
    const drawer = page.locator('.ant-drawer-open').filter({hasText: '应收明细'}).first();
    await expect(drawer).toContainText('净应收为负数');
    await expect(drawer).toContainText('-3.5000');
    await expect(drawer).toContainText('超额核销待处理');
});
