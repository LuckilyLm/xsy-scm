import {test, expect} from './scm-test-base';

const root = 'http://127.0.0.1:18083/e2e/fixtures/adm-ui.html';
const order = {orderId: 1, orderNo: 'SO-授信验收', version: 3, status: 'PENDING', customerId: 2,
  customerNameSnapshot: '验收客户', customerCodeSnapshot: 'ADM-C', settleModeSnapshot: 'INDEPENDENT',
  orderedTotalAmount: '10.0000', settlementTotalAmount: null, items: [], address: {receiverName: '收货人', receiverPhone: '13800000000', address: '验收地址'}};

async function mock(page: any, handler: (path: string, body: any, request: any) => unknown) {
  await page.route('**/*', async (route: any) => {
    const req = route.request();
    const url = new URL(req.url());
    if (!url.pathname.startsWith('/scm/') && !url.pathname.startsWith('/tableColumn/')) return route.continue();
    let body;
    try { body = req.postDataJSON(); } catch { body = undefined; }
    const data = handler(url.pathname, body, req);
    await route.fulfill({contentType: 'application/json', body: JSON.stringify({code: 0, ok: true, data: data ?? [], msg: 'success'})});
  });
}

test('授信阻断提示与例外原因随确认命令提交', async ({page}) => {
  let confirmation: any;
  await mock(page, (path, body, req) => {
    if (path === '/scm/order/detail/1') return order;
    if (path === '/scm/order/credit-check/order/1') return {allowed: false, overLimit: true, overdue: false,
      creditLimit: '15.0000', projectedExposure: '20.0000', openReceivableAmount: '10.0000',
      confirmedOrderAmount: '0.0000', requestedOrderAmount: '10.0000'};
    if (path === '/scm/order/confirm') { confirmation = {body, key: req.headers()['idempotency-key']}; return {...order, status: 'CONFIRMED'}; }
  });
  await page.goto(root + '?mode=credit');
  await page.getByRole('button', {name: '确认订单', exact: true}).click();
  const modal = page.locator('.ant-modal:visible');
  await expect(modal).toContainText('额度不足或存在逾期');
  await expect(modal.locator('.ant-modal-footer .ant-btn-primary')).toBeDisabled();
  await modal.getByRole('checkbox').check();
  await modal.locator('textarea').fill('主管批准本次临时额度');
  await expect(modal.locator('.ant-modal-footer .ant-btn-primary')).toBeEnabled();
  await page.screenshot({path: '../.runtime/adm-credit-desktop.png'});
  await page.setViewportSize({width: 390, height: 844});
  await page.screenshot({path: '../.runtime/adm-credit-mobile.png'});
  await modal.locator('.ant-modal-footer .ant-btn-primary').click();
  await expect(modal).toBeHidden();
  expect(confirmation.body).toMatchObject({orderId: 1, version: 3, creditOverride: true, creditOverrideReason: '主管批准本次临时额度'});
  expect(confirmation.key).toMatch(/^[0-9a-f-]{36}$/);
});

test('部分退货仅提交剩余数量且无需不可见的处理原因', async ({page}) => {
  let receipt: any;
  const returned = {returnId: 11, returnNo: 'RT-实物验收', orderId: 1, version: 2, status: 'APPROVED',
    approvedAmount: '50.0000', reason: '实物验收', items: [{returnItemId: 12, orderItemId: 13,
      productName: '验收青菜', unit: 'kg', requestedQuantity: '5.0000', approvedQuantity: '5.0000', receivedQuantity: '3.0000'}]};
  await mock(page, (path, body, req) => {
    if (path === '/scm/order/return/query') return {list: [returned], total: 1, pageNum: 1, pageSize: 20};
    if (path === '/scm/order/return/detail/11') return returned;
    if (path === '/scm/warehouse/list') return [{id: 9, name: '验收仓库', warehouseCode: 'WH-ADM'}];
    if (path === '/scm/order/return/receive') { receipt = {body, key: req.headers()['idempotency-key']}; return {receiptId: 14}; }
  });
  await page.goto(root + '?mode=return');
  await page.getByRole('button', {name: '实物接收', exact: true}).click();
  const modal = page.locator('.ant-modal:visible');
  await expect(modal.getByLabel('本次接收数量')).toHaveValue('2.0000');
  await modal.locator('.ant-form-item').filter({hasText: '接收仓库'}).locator('.ant-select-selector').click();
  await page.locator('.ant-select-dropdown:visible').getByText('验收仓库', {exact: false}).first().click();
  await modal.locator('.ant-modal-title').click();
  await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0);
  await page.screenshot({path: '../.runtime/adm-return-desktop.png'});
  await page.setViewportSize({width: 390, height: 844});
  await page.screenshot({path: '../.runtime/adm-return-mobile.png'});
  await modal.locator('.ant-modal-footer .ant-btn-primary').click();
  await expect(modal).toBeHidden();
  expect(receipt.body).toEqual({returnId: 11, version: 2, warehouseId: 9,
    items: [{returnItemId: 12, quantity: '2.0000', disposition: 'RETURN_TO_STOCK'}]});
  expect(receipt.key).toMatch(/^[0-9a-f-]{36}$/);
});
