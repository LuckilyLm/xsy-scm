/*
 * 独立退货单（不关联原单）浏览器侧验收。
 *
 * 账务口径（最近成交价、客户级红字、按客户先进先出抵减）已由 `OrderReturnIndependentPgIT` 覆盖，
 * 这里只验页面上能看见的：按客户 + 商品建单、建议价来自最近一次成交、列表与详情如实标「独立退货」、
 * 批准后不出现原单专属的换货入口。
 */
import {test, expect} from './scm-test-base';
import {randomUUID} from 'node:crypto';
import type {APIRequestContext, Page} from '@playwright/test';
import {accessibleName, apiClient, authenticate, login, provisionTempAccounts, type TempAccounts} from './scm-e2e-account';
import {createLocatedCustomer, createSku} from './scm-delivery-fixtures';

type Row = Record<string, any>;

async function call<T = Row>(client: APIRequestContext, method: 'get' | 'post', path: string, data?: unknown): Promise<T> {
  const response = method === 'get' ? await client.get(path) : await client.post(path, {data, headers: {'Idempotency-Key': randomUUID()}});
  const body = await response.json();
  if (body.code !== 0) throw new Error(`${method.toUpperCase()} ${path} 失败：HTTP ${response.status()} ${JSON.stringify(body)}`);
  return body.data as T;
}

test.describe('scm-return-independent', () => {
  test.describe.configure({mode: 'serial'});

  let accounts: TempAccounts;
  let client: APIRequestContext;
  let runTag: string;
  let token: string;
  let customerId: string;
  let customerName: string;
  let skuId: string;
  let returnNo: string;
  let recentPrice: string;

  test.beforeAll(async () => {
    accounts = provisionTempAccounts('w1');
    token = await login(accounts, accounts.admin);
    client = await apiClient(token);
    runTag = `IR${Date.now().toString(36).toUpperCase()}`;
    customerId = await createLocatedCustomer(client, runTag, 'A', '浙江省杭州市西湖区文一西路 100 号');
    customerName = `${runTag}客户A`;
    skuId = await createSku(client, runTag, 'A');
    // 独立退货的「最近成交价」来自已确认订单；没有这张单就只能走人工填价，验不到取价链路。
    let order = await call<Row>(client, 'post', '/scm/order/create', {
      customerId,
      orderSource: 'ADMIN',
      address: {receiverName: `${runTag}收货人`, receiverPhone: '13800000000', address: `${runTag}验收路 1 号`},
      remark: `${runTag} 独立退货取价夹具`,
      items: [{skuId, orderedQuantity: '10.0000', manualPriceOverride: false}],
    });
    order = await call<Row>(client, 'post', '/scm/order/submit', {orderId: order.orderId, version: order.version});
    order = await call<Row>(client, 'post', '/scm/order/item/actual-quantity', {
      orderId: order.orderId,
      itemId: order.items[0].itemId,
      version: order.items[0].version,
      actualQuantity: '10.0000',
      reason: `${runTag} 实称`,
    });
    order = await call<Row>(client, 'post', '/scm/order/confirm', {orderId: order.orderId, version: order.version});
    expect(order.status).toBe('CONFIRMED');
    recentPrice = order.items[0].lockedUnitPrice;
    expect(Number(recentPrice)).toBeGreaterThan(0);
  });

  test.afterAll(async () => {
    await client?.dispose();
    accounts?.cleanup();
  });

  async function browse(page: Page) {
    await authenticate(page, token);
    await page.goto('/#/order/order-return-list');
    await expect(page.locator('#order-return-table')).toBeVisible();
  }

  test('独立退货建单：建议价取最近成交价，列表标「独立退货」且没有原订单号', async ({page}) => {
    await browse(page);
    await page.getByRole('button', {name: accessibleName('新建独立退货')}).click();

    const modal = page.locator('.ant-modal:visible').filter({hasText: '新建独立退货'});
    await expect(modal).toBeVisible();

    const customerField = modal.locator('.ant-form-item').filter({hasText: '客户'});
    await customerField.locator('.ant-select-selector').click();
    await customerField.locator('input').fill(customerName);
    await page.locator('.ant-select-dropdown:visible').getByText(customerName, {exact: true}).click();

    const skuSelect = modal.locator('.ant-table-tbody .ant-select-selector').first();
    await skuSelect.click();
    await skuSelect.locator('input').fill(`${runTag}商品A`);
    await page.locator('.ant-select-dropdown:visible').getByText('散装A', {exact: false}).first().click();

    // 建单前就能看到建议价与来源，避免「先建单再发现价格为 0」
    await expect(modal.getByText(`建议价：${recentPrice}（最近成交价）`)).toBeVisible();
    await modal.locator('.ant-form-item').filter({hasText: '退货原因'}).locator('input').fill('现场退回，找不到原单');

    await modal.getByRole('button', {name: /^确\s*定$/}).click();
    await expect(page.getByText('独立退货单已创建', {exact: true})).toBeVisible();

    const created = await call<Row>(client, 'post', '/scm/order/return/query', {
      pageNum: 1,
      pageSize: 20,
      customerId: Number(customerId),
      sourceType: 'INDEPENDENT',
    });
    const row = (created.list as Row[]).find((item) => item.customerName === customerName);
    expect(row, '新建的独立退货应能在列表按客户 + 来源查到').toBeTruthy();
    returnNo = String(row!.returnNo);
    expect(row!.sourceType).toBe('INDEPENDENT');
    expect(row!.orderId).toBeNull();

    // 页面同步：来源筛选 + 关键字收敛到这一单，避免被开发库里的历史退货干扰
    await page.locator('.smart-query-form-item').filter({hasText: '来源'}).locator('.ant-select-selector').click();
    await page.locator('.ant-select-dropdown:visible').getByText('独立退货', {exact: true}).click();
    const keyword = page.locator('.smart-query-form-item').filter({hasText: '单号 / 客户'}).locator('input');
    await keyword.fill(returnNo);
    await page.getByRole('button', {name: accessibleName('查询')}).click();

    const tableRow = page.locator('#order-return-table tbody tr').filter({hasText: returnNo});
    await expect(tableRow).toHaveCount(1);
    await expect(tableRow).toContainText('独立退货');
    await expect(tableRow).toContainText('—');

    await tableRow.getByRole('button', {name: '详情'}).click();
    const drawer = page.locator('.ant-drawer-open').filter({hasText: '退货单详情'});
    await expect(drawer).toBeVisible();
    // a-descriptions bordered 渲染成表格行（label 在 rowheader 里），按行取一段即可
    await expect(drawer.locator('tr').filter({hasText: '来源'})).toContainText('独立退货');
    await expect(drawer.locator('tr').filter({hasText: '原订单号'})).toContainText('—');
    // 换货单按原订单行创建，独立退货没有原单，页面上不应出现该入口
    await expect(drawer.getByRole('button', {name: '创建关联换货单'})).toHaveCount(0);
  });

  test('独立退货审批：批准数量按退货行提交，状态推进到已批准', async ({page}) => {
    await browse(page);
    const keyword = page.locator('.smart-query-form-item').filter({hasText: '单号 / 客户'}).locator('input');
    await keyword.fill(returnNo);
    await page.getByRole('button', {name: accessibleName('查询')}).click();

    const tableRow = page.locator('#order-return-table tbody tr').filter({hasText: returnNo});
    await expect(tableRow).toHaveCount(1);
    await tableRow.getByRole('button', {name: '批准'}).click();

    const modal = page.locator('.ant-modal:visible').filter({hasText: '审核退货'});
    await expect(modal).toBeVisible();
    // 批准数量默认就是申请数量，这里显式写一次，确保提交的是页面上的值而不是服务端回填。
    await modal.locator('.ant-table-tbody input.ant-input-number-input').first().fill('1');
    await modal.getByRole('button', {name: /^确\s*定$/}).click();

    await expect(page.locator('#order-return-table tbody tr').filter({hasText: returnNo})).toContainText('已批准');

    const listed = await call<Row>(client, 'post', '/scm/order/return/query', {pageNum: 1, pageSize: 20, keyword: returnNo});
    const approved = await call<Row>(client, 'get', `/scm/order/return/detail/${(listed.list as Row[])[0].returnId}`);
    expect(approved.status).toBe('APPROVED');
    expect(Number(approved.approvedAmount)).toBeGreaterThan(0);
  });
});
