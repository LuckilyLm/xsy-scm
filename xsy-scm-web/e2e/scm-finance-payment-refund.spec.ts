import {test, expect} from './scm-test-base';
import {accessibleName, authenticate} from './scm-e2e-account';
import {
    call,
    completeRefund,
    createApprovedReturn,
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

test('只可选择已完成退款登记付款，重复来源被拒且应收不再次冲减', async ({page}) => {
    const facts = await createSignedOrder(harness, 'REFUND', [{quantity: '10.0000'}]);
    const approved = await createApprovedReturn(harness, facts, '1.0000', 'REFUND');
    const refund = await completeRefund(harness, approved.refund, 'REFUND-DONE');
    expect(refund.status).toBe('COMPLETED');
    const receivableBefore = await call<Row>(harness.finance, 'get', `/scm/finance/receivable/${facts.receivable.receivableId}`);

    await authenticate(page, harness.financeToken);
    await page.goto('/#/finance/payments');
    await expect(page.locator('#scm-finance-payment-table')).toBeVisible();
    await page.getByRole('button', {name: accessibleName('登记付款')}).click();
    const form = page.locator('.ant-modal:visible').filter({hasText: '登记付款'}).last();
    await form.locator('.ant-form-item').filter({hasText: '往来方类型'}).locator('.ant-select-selector').click();
    await page.locator('.ant-select-dropdown:visible').getByText('客户', {exact: true}).click();
    await form.getByRole('button', {name: accessibleName('选择退款')}).click();
    const picker = page.locator('.ant-modal:visible').filter({hasText: '选择已完成退款'}).last();
    await picker.getByPlaceholder('输入退款单号、订单号或客户名称').fill(String(refund.refundNo));
    await picker.getByRole('button', {name: accessibleName('搜索可付款退款')}).click();
    const refundRow = picker.locator('tr').filter({hasText: String(refund.refundNo)}).first();
    await expect(refundRow).toContainText('3.5000');
    await refundRow.getByRole('button', {name: accessibleName('选择')}).click();
    await expect(picker).toHaveCount(0);
    await expect(form.locator('input[disabled]')).toHaveValue(/3\.5000/);
    await form.getByRole('button', {name: /确\s*定/}).click();
    await expect(form).toHaveCount(0);
    await expect(page.getByText('付款已登记')).toBeVisible();

    const duplicate = await envelope(harness.finance, 'post', '/scm/finance/payment/add', {
        counterpartyType: 'CUSTOMER',
        counterpartyId: Number(facts.customerId),
        amount: String(refund.refundAmount),
        method: 'BANK_TRANSFER',
        paidAt: new Date().toISOString(),
        externalReference: `${harness.runTag}-duplicate-refund`,
        remark: `${harness.runTag} duplicate refund payment`,
        sourceType: 'ORDER_REFUND',
        sourceId: Number(refund.refundId),
    });
    expect(duplicate.code).not.toBe(0);

    const payments = await call<Row>(harness.finance, 'post', '/scm/finance/payment/query', {
        ...financeDateRange(), pageNum: 1, pageSize: 100,
        counterpartyType: 'CUSTOMER', counterpartyId: Number(facts.customerId), sourceType: 'ORDER_REFUND',
    });
    const sourced = (payments.list as Row[]).filter((item) => Number(item.sourceId) === Number(refund.refundId));
    expect(sourced).toHaveLength(1);
    expect(sourced[0].amount).toBe('3.5000');
    const receivableAfter = await call<Row>(harness.finance, 'get', `/scm/finance/receivable/${facts.receivable.receivableId}`);
    expect(receivableAfter.receivable.amount).toBe(receivableBefore.receivable.amount);
    expect(receivableAfter.receivable.netAmount).toBe(receivableBefore.receivable.netAmount);
});
