import {test, expect} from './scm-test-base';
import {accessibleName, authenticate} from './scm-e2e-account';
import {
    call,
    createReceipt,
    createSignedOrder,
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

async function selectInDrawer(page: any, drawer: any, label: string, value: string) {
    await drawer.locator('.ant-form-item').filter({hasText: label}).locator('.ant-select-selector').click();
    await page.locator('.ant-select-dropdown:visible').getByText(value, {exact: true}).click();
}

async function chooseRecord(page: any, title: string, documentNo: string) {
    const picker = page.locator('.ant-modal:visible').filter({hasText: title}).last();
    await expect(picker).toBeVisible();
    const row = picker.locator('tr').filter({hasText: documentNo}).first();
    await expect(row).toBeVisible();
    await row.getByRole('button', {name: accessibleName('选择')}).click();
    await expect(picker).toHaveCount(0);
}

test('一笔预收分配到两张应收，撤销一条后金额回退且重复撤销被拒', async ({page}) => {
    const first = await createSignedOrder(harness, 'WOA', [{quantity: '10.0000'}]);
    const second = await createSignedOrder(harness, 'WOB', [{quantity: '10.0000'}], first.customerId);
    const receipt = await createReceipt(harness, first.customerId, '70.0000', 'WO-RECEIPT');
    expect(first.receivable.openAmount).toBe('35.0000');
    expect(second.receivable.openAmount).toBe('35.0000');

    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/write-offs');
    await expect(page.locator('#scm-finance-write-off-table')).toBeVisible();
    await page.getByRole('button', {name: accessibleName('登记核销')}).click();
    const drawer = page.locator('.ant-drawer-open').filter({hasText: '登记多目标核销'}).first();
    await selectInDrawer(page, drawer, '资金类型', '收款');
    await drawer.locator('.ant-form-item').filter({hasText: '资金单'}).getByRole('button', {name: accessibleName('选择')}).click();
    await chooseRecord(page, '选择待核销收款', receipt.receiptNo);

    await drawer.getByRole('button', {name: accessibleName('添加目标单')}).click();
    await chooseRecord(page, '选择未结清应收', first.receivable.receivableNo);
    await drawer.getByRole('button', {name: accessibleName('添加目标单')}).click();
    await chooseRecord(page, '选择未结清应收', second.receivable.receivableNo);
    const firstTarget = drawer.locator('.ant-table-row').filter({hasText: first.receivable.receivableNo}).first();
    const secondTarget = drawer.locator('.ant-table-row').filter({hasText: second.receivable.receivableNo}).first();
    await firstTarget.locator('.ant-input-number input').fill('20.0000');
    await secondTarget.locator('.ant-input-number input').fill('30.0000');
    await drawer.getByRole('button', {name: accessibleName('提交核销')}).click();
    await expect(page.locator('.ant-drawer-open')).toHaveCount(0);

    const applied = await call<Row>(harness.finance, 'post', '/scm/finance/write-off/query', {
        ...financeDateRange(), pageNum: 1, pageSize: 100, sourceNo: receipt.receiptNo,
    });
    expect(applied.list).toHaveLength(2);
    expect((applied.list as Row[]).map((item) => item.amount).sort()).toEqual(['20.0000', '30.0000']);

    const firstEntry = (applied.list as Row[]).find((item) => item.targetNo === first.receivable.receivableNo)!;
    const row = page.locator('#scm-finance-write-off-table tr').filter({hasText: firstEntry.writeOffNo}).first();
    await row.getByRole('button', {name: accessibleName('撤销核销')}).click();
    const dialog = page.locator('.ant-modal:visible').filter({hasText: '撤销核销'}).last();
    await dialog.locator('textarea').fill(`${harness.runTag} 撤销核销验收`);
    await dialog.getByRole('button', {name: /确\s*定/}).click();
    await expect(dialog).toHaveCount(0);

    const after = await call<Row>(harness.finance, 'post', '/scm/finance/write-off/query', {
        ...financeDateRange(), pageNum: 1, pageSize: 100, sourceNo: receipt.receiptNo,
    });
    expect(after.list).toHaveLength(3);
    expect((after.list as Row[]).filter((item) => item.entryType === 'REVERSE')).toHaveLength(1);
    const duplicate = await envelope(harness.finance, 'post', '/scm/finance/write-off/reverse', {
        writeOffId: Number(firstEntry.writeOffId), reason: `${harness.runTag} 重复撤销`,
    });
    expect(duplicate.code).not.toBe(0);
});
