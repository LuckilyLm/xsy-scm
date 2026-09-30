import type {Browser, Page} from '@playwright/test';
import {test, expect} from './scm-test-base';
import {accessibleName, apiClient, authenticate, login} from './scm-e2e-account';
import {
    call,
    createSignedOrder,
    financeDateRange,
    openFinanceHarness,
    type FinanceHarness,
    type Row,
} from './scm-finance-fixtures';

test.describe.configure({mode: 'serial'});

let harness: FinanceHarness;
test.beforeAll(async () => { harness = await openFinanceHarness(); });
test.afterAll(async () => { if (harness) await harness.close(); });

async function checkExtraPage(page: Page, use: () => Promise<void>) {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.addInitScript(() => {
        window.addEventListener('unhandledrejection', (event) => {
            throw event.reason instanceof Error ? event.reason : new Error(String(event.reason));
        });
    });
    await use();
    expect(errors, `browser page errors: ${errors.join(' | ')}`).toEqual([]);
}

test('财务角色可用；销售深链和接口被拒，只读账号隐藏导出及反向并在接口层被拒', async ({page, browser}) => {
    const facts = await createSignedOrder(harness, 'PERM', [{quantity: '10.0000'}]);

    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/receipts');
    await expect(page.locator('#scm-finance-receipt-table')).toBeVisible();
    await page.getByRole('button', {name: accessibleName('登记收款')}).click();
    const addForm = page.locator('.ant-modal:visible').filter({hasText: '登记收款'}).last();
    const customerPicker = addForm.locator('.ant-form-item').filter({hasText: '客户'}).locator('.ant-select-selector');
    await customerPicker.click();
    const customerSearch = customerPicker.locator('input').last();
    await customerSearch.fill(facts.customerName);
    await page.locator('.ant-select-dropdown:visible .ant-select-item-option-content')
        .filter({hasText: facts.customerName}).first().click();
    await addForm.locator('.ant-form-item').filter({hasText: '收款金额'}).locator('.ant-input-number input').fill('9.0000');
    await addForm.locator('.ant-form-item').filter({hasText: '收款方式'}).locator('.ant-select-selector').click();
    await page.locator('.ant-select-dropdown:visible').getByText('银行转账', {exact: true}).click();
    await addForm.locator('.ant-form-item').filter({hasText: '资金凭据号'}).locator('input')
        .fill(`${harness.runTag}-PERMISSION-RECEIPT`);
    await addForm.getByRole('button', {name: /确\s*定/}).click();
    await expect(addForm).toHaveCount(0);
    await expect(page.getByText('收款已登记')).toBeVisible();

    const receipts = await call<Row>(harness.finance, 'post', '/scm/finance/receipt/query', {
        ...financeDateRange(), pageNum: 1, pageSize: 100, customerId: Number(facts.customerId),
    });
    const receipt = (receipts.list as Row[]).find((item) => item.externalReference === `${harness.runTag}-PERMISSION-RECEIPT`)!;
    expect(receipt.amount).toBe('9.0000');

    const salesPage = await browser.newPage();
    await checkExtraPage(salesPage, async () => {
        await authenticate(salesPage, harness.salesToken);
        await salesPage.goto('http://127.0.0.1:18081/#/finance/receivables');
        await expect(salesPage.getByText('您访问的内容不存在')).toBeVisible();
    });
    await salesPage.close();
    const salesApi = await apiClient(harness.salesToken);
    const salesDenied = await (await salesApi.post('/scm/finance/receivable/query', {
        data: {...financeDateRange(), pageNum: 1, pageSize: 20},
    })).json();
    await salesApi.dispose();
    expect(salesDenied.code).toBe(30005);

    const readOnlyToken = await login(harness.accounts, harness.accounts.readOnly);
    const readOnlyApi = await apiClient(readOnlyToken);
    const noExport = await (await readOnlyApi.post('/scm/finance/receipt/export', {
        data: {...financeDateRange(), pageNum: 1, pageSize: 20},
    })).json();
    expect(noExport.code).toBe(30005);
    const noReverse = await (await readOnlyApi.post('/scm/finance/receipt/reverse', {
        data: {receiptId: Number(receipt.receiptId), reason: `${harness.runTag} 未授权反向`},
    })).json();
    await readOnlyApi.dispose();
    expect(noReverse.code).toBe(30005);

    const readOnlyPage = await browser.newPage();
    await checkExtraPage(readOnlyPage, async () => {
        await authenticate(readOnlyPage, readOnlyToken);
        await readOnlyPage.goto('http://127.0.0.1:18081/#/finance/receipts');
        const table = readOnlyPage.locator('#scm-finance-receipt-table');
        await expect(table).toBeVisible();
        const row = table.locator('tr').filter({hasText: receipt.receiptNo}).first();
        await expect(row).toBeVisible();
        await expect(readOnlyPage.getByRole('button', {name: accessibleName('导出')})).toHaveCount(0);
        await expect(row.getByRole('button', {name: accessibleName('反向')})).toHaveCount(0);
    });
    await readOnlyPage.close();
});
