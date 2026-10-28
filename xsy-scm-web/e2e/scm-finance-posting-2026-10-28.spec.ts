/*
 * 过账特殊单据（D-1028-16）浏览器侧验收。
 *
 * 覆盖页面上能看见的闭环：登记 → 列表 → 修改 → 作废。
 * 「不生成应收应付、不影响对账余额」由 FinancePostingIT / StatementPostingOptionPgIT 覆盖，
 * 这里只确认页面确实按这套口径把单据写进了过账表，而不是写成了应收。
 */
import {test, expect} from './scm-test-base';
import type {APIRequestContext, Page} from '@playwright/test';
import {accessibleName, apiClient, authenticate, login, provisionTempAccounts, type TempAccounts} from './scm-e2e-account';
import {createLocatedCustomer} from './scm-delivery-fixtures';

type Row = Record<string, any>;

async function call<T = Row>(client: APIRequestContext, method: 'get' | 'post', path: string, data?: unknown): Promise<T> {
  const response = method === 'get' ? await client.get(path) : await client.post(path, {data});
  const body = await response.json();
  if (body.code !== 0) throw new Error(`${method.toUpperCase()} ${path} 失败：HTTP ${response.status()} ${JSON.stringify(body)}`);
  return body.data as T;
}

test.describe('scm-finance-posting', () => {
  test.describe.configure({mode: 'serial'});

  let accounts: TempAccounts;
  let client: APIRequestContext;
  let runTag: string;
  let token: string;
  let customerId: string;
  let customerName: string;
  /** 对账/过账筛选按业务日（Asia/Shanghai），不能用 UTC 的 toISOString 切日期 */
  let today: string;

  test.beforeAll(async () => {
    accounts = provisionTempAccounts('w1');
    token = await login(accounts, accounts.admin);
    client = await apiClient(token);
    runTag = `PD${Date.now().toString(36).toUpperCase()}`;
    const now = new Date();
    today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    customerId = await createLocatedCustomer(client, runTag, 'A', '浙江省杭州市西湖区文一西路 200 号');
    customerName = `${runTag}客户A`;
  });

  test.afterAll(async () => {
    await client?.dispose();
    accounts?.cleanup();
  });

  async function browse(page: Page) {
    await authenticate(page, token);
    await page.goto('/#/finance/postings');
    await expect(page.locator('#scm-finance-posting-table')).toBeVisible();
  }

  test('登记过账：按客户登记金额与税点，列表显示未返款', async ({page}) => {
    await browse(page);
    await page.getByRole('button', {name: accessibleName('新建过账')}).click();

    const modal = page.locator('.ant-modal:visible').filter({hasText: '新建过账'});
    await expect(modal).toBeVisible();
    // 页面必须说明过账不进应收应付，否则财务会以为这是给客户挂账
    await expect(modal.getByText('不生成应收应付')).toBeVisible();

    const customerField = modal.locator('.ant-form-item').filter({hasText: '客户'});
    await customerField.locator('.ant-select-selector').click();
    await customerField.locator('input').fill(customerName);
    await page.locator('.ant-select-dropdown:visible').getByText(customerName, {exact: true}).click();

    const amountInput = modal.locator('.ant-form-item').filter({hasText: '过账金额'}).locator('input');
    await amountInput.fill('1234.5000');
    await modal.locator('.ant-form-item').filter({hasText: '税点'}).locator('input').fill('13');
    await modal.locator('.ant-form-item').filter({hasText: '备注'}).locator('textarea').fill(`${runTag} 过账验收集`);

    await modal.getByRole('button', {name: /^确\s*定$/}).click();
    await expect(page.getByText('过账已登记', {exact: true})).toBeVisible();

    const created = await call<Row>(client, 'post', '/scm/finance/posting/query', {
      pageNum: 1,
      pageSize: 20,
      startDate: today,
      endDate: today,
      customerId: Number(customerId),
    });
    const row = (created.list as Row[])[0];
    expect(row, '登记后应能按客户查到这张过账单').toBeTruthy();
    expect(row.postingNo).toMatch(/^PD/);
    expect(row.refundStatus).toBe('PENDING');
    expect(row.status).toBe('NORMAL');
    expect(Number(row.amount)).toBeCloseTo(1234.5, 4);

    // 过账不进应收：同客户当天没有新增应收
    const receivables = await call<Row>(client, 'post', '/scm/finance/receivable/query', {
      pageNum: 1,
      pageSize: 20,
      startDate: today,
      endDate: today,
      customerId: Number(customerId),
    });
    expect((receivables.list as Row[]).length).toBe(0);

    const keyword = page.locator('.smart-query-form .ant-form-item').filter({hasText: '单号 / 客户'}).locator('input');
    await keyword.fill(String(row.postingNo));
    await page.getByRole('button', {name: accessibleName('查询')}).click();
    const tableRow = page.locator('#scm-finance-posting-table tbody tr').filter({hasText: String(row.postingNo)});
    await expect(tableRow).toHaveCount(1);
    await expect(tableRow).toContainText('未返款');
    await expect(tableRow).toContainText('¥ 1,234.5000');
  });

  test('修改与作废：改金额留痕，作废必须写原因且不可再改', async ({page}) => {
    await browse(page);
    const listed = await call<Row>(client, 'post', '/scm/finance/posting/query', {
      pageNum: 1,
      pageSize: 20,
      startDate: today,
      endDate: today,
      customerId: Number(customerId),
    });
    const row = (listed.list as Row[])[0];
    const keyword = page.locator('.smart-query-form .ant-form-item').filter({hasText: '单号 / 客户'}).locator('input');
    await keyword.fill(String(row.postingNo));
    await page.getByRole('button', {name: accessibleName('查询')}).click();
    const tableRow = page.locator('#scm-finance-posting-table tbody tr').filter({hasText: String(row.postingNo)});
    await expect(tableRow).toHaveCount(1);

    await tableRow.getByRole('button', {name: '修改'}).click();
    const editModal = page.locator('.ant-modal:visible').filter({hasText: '修改过账'});
    await expect(editModal).toBeVisible();
    const amountInput = editModal.locator('.ant-form-item').filter({hasText: '过账金额'}).locator('input');
    await amountInput.fill('2000');
    await editModal.getByRole('button', {name: /^确\s*定$/}).click();
    await expect(page.getByText('过账已修改', {exact: true})).toBeVisible();

    const updated = await call<Row>(client, 'get', `/scm/finance/posting/${row.postingId}`);
    expect(Number(updated.amount)).toBeCloseTo(2000, 4);
    const logs = await call<Row[]>(client, 'get', `/scm/finance/log/query?businessType=POSTING&businessId=${row.postingId}`);
    expect(logs.map((log) => log.operationType)).toEqual(['POSTING_ADD', 'POSTING_UPDATE']);

    await tableRow.getByRole('button', {name: '作废'}).click();
    const voidModal = page.locator('.ant-modal:visible').filter({hasText: '作废过账'});
    await expect(voidModal).toBeVisible();
    await voidModal.getByRole('button', {name: /^确\s*定$/}).click();
    await expect(page.getByText('请填写作废原因', {exact: true})).toBeVisible();

    await voidModal.locator('textarea').fill(`${runTag} 金额录错`);
    await voidModal.getByRole('button', {name: /^确\s*定$/}).click();
    await expect(page.getByText('过账已作废', {exact: true})).toBeVisible();

    const voided = await call<Row>(client, 'get', `/scm/finance/posting/${row.postingId}`);
    expect(voided.status).toBe('VOIDED');
    expect(voided.voidReason).toBe(`${runTag} 金额录错`);
    // 作废后是终态：行还在，金额不被改写，修改/作废入口消失
    expect(Number(voided.amount)).toBeCloseTo(2000, 4);
    await expect(tableRow).toContainText('已作废');
    await expect(tableRow.getByRole('button', {name: '修改'})).toHaveCount(0);
    await expect(tableRow.getByRole('button', {name: '作废'})).toHaveCount(0);
  });
});
