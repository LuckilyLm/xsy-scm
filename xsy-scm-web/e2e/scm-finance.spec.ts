/*
 * 本文件由按域拆分的 E2E 用例合并而成：每个 test.describe 对应一份原文件，
 * 块内状态与钩子互不共享，执行顺序按来源文件排列。
 */
import {test, expect} from './scm-test-base';
import {readFileSync} from 'node:fs';
import {accessibleName, authenticate, apiClient, login} from './scm-e2e-account';
import {call, createApprovedReturn, createReceipt, createSignedOrder, createWriteOff, openFinanceHarness, type FinanceHarness, type Row, createPurchaseReceipt, envelope, completeRefund, financeDateRange} from './scm-finance-fixtures';
import {type Page} from '@playwright/test';

// ---------------------------------------------------------------- 来源：e2e/scm-finance-overview.spec.ts
test.describe('scm-finance-overview', () => {

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

  /** Aging-free detail endpoints expose all normal documents before endDate; paginate for stock totals. */
  async function financeStockRows(path: string, range: Record<string, string>): Promise<Row[]> {
      const rows: Row[] = [];
      let total = Number.POSITIVE_INFINITY;
      for (let pageNum = 1; rows.length < total; pageNum++) {
          const page = await call<Row>(harness.finance, 'post', path, { ...range, pageNum, pageSize: 100 });
          const pageRows = page.list as Row[];
          rows.push(...pageRows);
          total = Number(page.total);
          if (pageRows.length === 0) break;
      }
      expect(rows.length, `${path} must include every document before endDate`).toBe(total);
      return rows;
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
      const [receivables, payables, writeOffs, receivableStock, payableStock] = await Promise.all([
          call<Row>(harness.finance, 'post', '/scm/finance/receivable/query', {
              ...range, pageNum: 1, pageSize: 100,
          }),
          call<Row>(harness.finance, 'post', '/scm/finance/payable/query', {
              ...range, pageNum: 1, pageSize: 100,
          }),
          call<Row>(harness.finance, 'post', '/scm/finance/write-off/query', {
              ...range, pageNum: 1, pageSize: 100,
          }),
          financeStockRows('/scm/report/finance/receivable/aging-free-detail', range),
          financeStockRows('/scm/report/finance/payable/aging-free-detail', range),
      ]);
      expect(receivables.list.length).toBe(receivables.total);
      expect(payables.list.length).toBe(payables.total);
      expect(writeOffs.list.length).toBe(writeOffs.total);

      const targetWriteOffs = (targetType: string) => (writeOffs.list as Row[])
          .filter((item) => item.targetType === targetType);
      const expected = {
          receivableOccurredAmount: signedTotal(receivables.list as Row[], 'RED'),
          receivableWrittenOffAmount: signedTotal(targetWriteOffs('RECEIVABLE'), 'REVERSE'),
          endingReceivableAmount: fixed(receivableStock
              .reduce((sum, item) => sum + scaled(String(item.openAmount)), 0n)),
          payableOccurredAmount: signedTotal(payables.list as Row[], 'RED'),
          payableWrittenOffAmount: signedTotal(targetWriteOffs('PAYABLE'), 'REVERSE'),
          endingPayableAmount: fixed(payableStock
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
      // 「已核销」的口径说明属于常驻长句，已被文案契约清退；口径本身由上面的 KPI 断言覆盖。
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

});

// ---------------------------------------------------------------- 来源：e2e/scm-finance-payable.spec.ts
test.describe('scm-finance-payable', () => {

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

});

// ---------------------------------------------------------------- 来源：e2e/scm-finance-payment-refund.spec.ts
test.describe('scm-finance-payment-refund', () => {

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

});

// ---------------------------------------------------------------- 来源：e2e/scm-finance-permission.spec.ts
test.describe('scm-finance-permission', () => {

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

});

// ---------------------------------------------------------------- 来源：e2e/scm-finance-receipt-reverse.spec.ts
test.describe('scm-finance-receipt-reverse', () => {

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
      await row.getByRole('button', {name: accessibleName('撤销收款')}).click();
      const dialog = page.locator('.ant-modal:visible').filter({hasText: '撤销收款'}).last();
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

});

// ---------------------------------------------------------------- 来源：e2e/scm-finance-receivable.spec.ts
test.describe('scm-finance-receivable', () => {

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

});

// ---------------------------------------------------------------- 来源：e2e/scm-finance-receivable-red.spec.ts
test.describe('scm-finance-receivable-red', () => {

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
      // 桌面表格用「超额核销」列（异常色）表达同一事实；「超额核销待处理」标签只在移动端布局与明细抽屉里
      await expect(row).toContainText('3.5000');
      await row.getByRole('button', {name: accessibleName('明细')}).click();
      const drawer = page.locator('.ant-drawer-open').filter({hasText: '应收明细'}).first();
      await expect(drawer).toContainText('净应收为负数');
      await expect(drawer).toContainText('-3.5000');
      await expect(drawer).toContainText('超额核销待处理');
  });

});

// ---------------------------------------------------------------- 来源：e2e/scm-finance-write-off.spec.ts
test.describe('scm-finance-write-off', () => {

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

});
