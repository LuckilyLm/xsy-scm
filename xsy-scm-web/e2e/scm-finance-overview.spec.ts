import {readFileSync} from 'node:fs';
import {test, expect} from './scm-test-base';
import {accessibleName, authenticate} from './scm-e2e-account';
import {
    call,
    completeRefund,
    createApprovedReturn,
    createReceipt,
    createSignedOrder,
    createWriteOff,
    openFinanceHarness,
    type FinanceHarness,
    type Row,
} from './scm-finance-fixtures';

test.describe.configure({mode: 'serial'});

let harness: FinanceHarness;
test.beforeAll(async () => { harness = await openFinanceHarness(); });
test.afterAll(async () => { if (harness) await harness.close(); });

function reportDateRange() {
    const endDate = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai'}).format(new Date());
    return {startDate: `${endDate.slice(0, 7)}-01`, endDate};
}

function scaled(value: string) {
    const [whole, fraction = ''] = value.split('.');
    return BigInt(whole) * 10000n + BigInt((fraction + '0000').slice(0, 4));
}

function fixed(value: bigint) {
    const negative = value < 0n;
    const absolute = negative ? -value : value;
    return `${negative ? '-' : ''}${absolute / 10000n}.${String(absolute % 10000n).padStart(4, '0')}`;
}

function signedTotal(rows: Row[], negativeType: string) {
    return fixed(rows.reduce((total, row) => total +
        (row.entryType === negativeType ? -scaled(String(row.amount)) : scaled(String(row.amount))), 0n));
}

async function captureDownload(page: any, label: string, filename: string) {
    const pending = page.waitForEvent('download');
    await page.getByRole('button', {name: accessibleName(label)}).click();
    const download = await pending;
    expect(download.suggestedFilename()).toBe(filename);
    const bytes = readFileSync((await download.path())!);
    expect(bytes.subarray(0, 2).toString()).toBe('PK');
}

test('Finance R0 六指标与期末明细和 Finance R1 事实一致，并导出 XLSX', async ({page}) => {
    const facts = await createSignedOrder(harness, 'R0', [{quantity: '10.0000'}]);
    const returnFacts = await createApprovedReturn(harness, facts, '1.0000', 'R0-RETURN');
    expect(returnFacts.approved.status).toBe('APPROVED');
    const receipt = await createReceipt(harness, facts.customerId, '12.0000', 'R0-RECEIPT');
    await createWriteOff(harness, receipt.receiptId, [
        {targetId: facts.receivable.receivableId, amount: '12.0000'},
    ]);

    const range = reportDateRange();
    const [receivables, payables, writeOffs] = await Promise.all([
        call<Row>(harness.finance, 'post', '/scm/finance/receivable/query', {
            ...range, pageNum: 1, pageSize: 100,
        }),
        call<Row>(harness.finance, 'post', '/scm/finance/payable/query', {
            ...range, pageNum: 1, pageSize: 100,
        }),
        call<Row>(harness.finance, 'post', '/scm/finance/write-off/query', {
            ...range, pageNum: 1, pageSize: 100,
        }),
    ]);
    expect(receivables.list.length).toBe(receivables.total);
    expect(payables.list.length).toBe(payables.total);
    expect(writeOffs.list.length).toBe(writeOffs.total);

    const targetWriteOffs = (targetType: string) => (writeOffs.list as Row[])
        .filter((item) => item.targetType === targetType);
    const expected = {
        receivableOccurredAmount: signedTotal(receivables.list as Row[], 'RED'),
        receivableWrittenOffAmount: signedTotal(targetWriteOffs('RECEIVABLE'), 'REVERSE'),
        endingReceivableAmount: fixed((receivables.list as Row[])
            .reduce((sum, item) => sum + scaled(String(item.openAmount)), 0n)),
        payableOccurredAmount: signedTotal(payables.list as Row[], 'RED'),
        payableWrittenOffAmount: signedTotal(targetWriteOffs('PAYABLE'), 'REVERSE'),
        endingPayableAmount: fixed((payables.list as Row[])
            .reduce((sum, item) => sum + scaled(String(item.openAmount)), 0n)),
    };
    const orderReceivables = (receivables.list as Row[])
        .filter((item) => Number(item.orderId) === Number(facts.orderId));
    expect(signedTotal(orderReceivables, 'RED')).toBe('31.5000');
    const report = await call<Row>(harness.finance, 'post', '/scm/report/finance/overview', range);
    expect(report).toMatchObject(expected);

    await authenticate(page, harness.financeToken);
    await page.goto('/#/report/report-finance-overview');
    await expect(page.locator('.report-kpi')).toHaveCount(6);
    for (const [label, amount] of [
        ['应收发生额', expected.receivableOccurredAmount],
        ['应收已核销', expected.receivableWrittenOffAmount],
        ['期末待收', expected.endingReceivableAmount],
        ['应付发生额', expected.payableOccurredAmount],
        ['应付已核销', expected.payableWrittenOffAmount],
        ['期末待付', expected.endingPayableAmount],
    ]) {
        const card = page.locator('.report-kpi').filter({hasText: label}).first();
        await expect(card.locator('.report-kpi-value')).toHaveText(amount);
    }
    await expect(page.getByText('已核销来自收付款与应收/应付之间的分配关系，不代表实际现金收付。', {exact: false})).toBeVisible();
    await captureDownload(page, '导出概览', '往来概览.xlsx');

    await page.getByPlaceholder('应收单号 / 订单号 / 客户').fill(facts.receivable.receivableNo);
    await page.locator('.ant-tabs-tabpane-active').getByRole('button', {name: accessibleName('查询')}).click();
    const receivableRow = page.locator('#scm-report-finance-receivable-table tr')
        .filter({hasText: facts.receivable.receivableNo}).first();
    await expect(receivableRow).toContainText('19.5000');
    await captureDownload(page, '导出应收', '应收明细.xlsx');

    await page.locator('.ant-tabs-tab').filter({hasText: '应付明细'}).click();
    const supplierPayables = await call<Row>(harness.finance, 'post', '/scm/finance/payable/query', {
        ...range, pageNum: 1, pageSize: 100, supplierId: Number(facts.supplierId),
    });
    const payable = (supplierPayables.list as Row[])[0];
    await page.getByPlaceholder('应付单号 / 采购单号 / 供应商').fill(payable.payableNo);
    await page.locator('.ant-tabs-tabpane-active').getByRole('button', {name: accessibleName('查询')}).click();
    const payableRow = page.locator('#scm-report-finance-payable-table tr')
        .filter({hasText: payable.payableNo}).first();
    await expect(payableRow).toContainText(payable.amount);
    await captureDownload(page, '导出应付', '应付明细.xlsx');
});

test('Finance R0 查询权限在服务端与页面路由同时收口', async ({page}) => {
    const denied = await (await harness.sales.post('/scm/report/finance/overview', {data: reportDateRange()})).json();
    expect(denied.code).toBe(30005);
    await authenticate(page, harness.salesToken);
    await page.goto('/#/report/report-finance-overview');
    await expect(page.getByText('您访问的内容不存在')).toBeVisible();
});

test('Finance R0 mobile detail cards expose every balance without horizontal table scrolling', async ({page}) => {
    await authenticate(page, harness.financeToken);
    await page.setViewportSize({width: 390, height: 844});
    await page.goto('/#/report/report-finance-overview');

    const receivableList = page.locator('.ant-tabs-tabpane-active .finance-detail-mobile-list');
    await expect(page.locator('#smartAdminMenu')).toHaveClass(/ant-layout-sider-zero-width/);
    await expect(page.locator('.layout-header-right .name')).toBeHidden();
    await expect(receivableList).toBeVisible();
    await expect(receivableList.locator('.finance-detail-mobile-card').first()).toContainText('应收单号');
    await expect(receivableList.locator('.finance-detail-mobile-card').first()).toContainText('期末待收');
    await expect(receivableList.locator('.finance-detail-mobile-card').first()).toContainText('已核销金额');
    await expect(page.locator('#scm-report-finance-receivable-table')).toBeHidden();

    const payableLoad = page.waitForResponse((response) =>
        response.url().includes('/scm/report/finance/payable/aging-free-detail') && response.request().method() === 'POST');
    await page.locator('.ant-tabs-tab').filter({hasText: '应付明细'}).click();
    await payableLoad;
    const payableList = page.locator('.ant-tabs-tabpane-active .finance-detail-mobile-list');
    await expect(payableList.locator('.finance-detail-mobile-card').first()).toContainText('应付单号');
    await expect(payableList.locator('.finance-detail-mobile-card').first()).toContainText('供应商');
    await expect(payableList.locator('.finance-detail-mobile-card').first()).toContainText('期末待付');
    await expect(payableList.locator('.finance-detail-mobile-card').first()).toContainText('事件时点');
    await expect(page.locator('#scm-report-finance-payable-table')).toBeHidden();
});
