import {test, expect} from './scm-test-base';
import {accessibleName, authenticate} from './scm-e2e-account';
import {
    call,
    createReceipt,
    createSignedOrder,
    createWriteOff,
    envelope,
    financeDateRange,
    openFinanceHarness,
    type FinanceHarness,
    type Row,
} from './scm-finance-fixtures';

test.describe.configure({mode: 'serial'});

let harness: FinanceHarness;
test.beforeAll(async () => { harness = await openFinanceHarness(); });
test.afterAll(async () => { if (harness) await harness.close(); });

test('已核销收款不能反向；先反向核销后追加反向收款', async ({page}) => {
    const facts = await createSignedOrder(harness, 'REV', [{quantity: '10.0000'}]);
    const receipt = await createReceipt(harness, facts.customerId, '30.0000', 'REV-RECEIPT');
    const writeOffResult = await createWriteOff(harness, receipt.receiptId, [
        {targetId: facts.receivable.receivableId, amount: '20.0000'},
    ]);
    const writeOff = (writeOffResult.items as Row[])[0];

    const blocked = await envelope(harness.finance, 'post', '/scm/finance/receipt/reverse', {
        receiptId: Number(receipt.receiptId), reason: `${harness.runTag} 已核销时反向`,
    });
    expect(blocked.code).toBe(41142);

    await call(harness.finance, 'post', '/scm/finance/write-off/reverse', {
        writeOffId: Number(writeOff.writeOffId), reason: `${harness.runTag} 先撤销核销`,
    });
    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/receipts');
    const row = page.locator('#scm-finance-receipt-table tr').filter({hasText: receipt.receiptNo}).first();
    await expect(row).toBeVisible();
    await expect(row).toContainText('30.0000');
    await row.getByRole('button', {name: accessibleName('反向')}).click();
    const dialog = page.locator('.ant-modal:visible').filter({hasText: '反向收款'}).last();
    await dialog.locator('textarea').fill(`${harness.runTag} 更正误录收款`);
    await dialog.getByRole('button', {name: /确\s*定/}).click();
    await expect(dialog).toHaveCount(0);

    const after = await call<Row>(harness.finance, 'post', '/scm/finance/receipt/query', {
        ...financeDateRange(), pageNum: 1, pageSize: 100, customerId: facts.customerId,
    });
    expect(after.list).toHaveLength(2);
    expect((after.list as Row[]).map((item) => item.entryType).sort()).toEqual(['NORMAL', 'REVERSE']);
    expect((after.list as Row[]).find((item) => item.entryType === 'NORMAL')?.effectiveAmount).toBe('0.0000');
    expect((after.list as Row[]).find((item) => item.entryType === 'REVERSE')?.reverseOfId)
        .toBe(receipt.receiptId);
});
