import {randomBytes, randomUUID} from 'node:crypto';
import {request, type APIRequestContext} from '@playwright/test';
import {test, expect} from '../e2e/scm-test-base';
import {apiClient, apiUrl, authenticate} from '../e2e/scm-e2e-account';
import {createLocatedCustomer, createSku} from '../e2e/scm-delivery-fixtures';
import {call, openFinanceHarness, type Row} from '../e2e/scm-finance-fixtures';

test('商城账号登录改密 → 零库存预订 → 重复提交只建一单 → 后台查看同一订单', async ({page}) => {
  const harness = await openFinanceHarness();
  let customer: APIRequestContext | undefined;
  const anonymous = await request.newContext({baseURL: apiUrl});
  try {
    const customerId = await createLocatedCustomer(harness.admin, harness.runTag, 'MALL', '联调收货地址');
    const customerDetails = await call<Row>(harness.admin, 'get', `/scm/customer/detail/${customerId}`);
    await call(harness.admin, 'post', '/scm/customer/update', {
      ...customerDetails,
      customerId,
      mallDeliveryWarehouseId: harness.warehouseId,
    });
    const skuId = await createSku(harness.admin, harness.runTag, 'MALL', 'STANDARD');
    const stock = await call<Row>(harness.admin, 'post', '/scm/inventory/balance/query', {
      pageNum: 1,
      pageSize: 10,
      warehouseId: harness.warehouseId,
      skuId,
    });
    expect(stock.list).toEqual([]);
    const phone = `139${Date.now().toString().slice(-8)}`;
    const temporaryPassword = `Tmp@${randomBytes(8).toString('hex')}`;
    await call(harness.admin, 'post', '/scm/customer/mall-account/create', {customerId, loginPhone: phone, temporaryPassword});
    const signedIn = await call<Row>(anonymous, 'post', '/scm/mall/auth/login', {phone, password: temporaryPassword});
    expect(signedIn.profile.firstPasswordChange).toBe(true);
    customer = await apiClient(signedIn.token);
    const newPassword = `New@${randomBytes(8).toString('hex')}`;
    const profile = await call<Row>(customer, 'post', '/scm/mall/auth/password/change', {
      oldPassword: temporaryPassword,
      newPassword,
    });
    expect(profile.firstPasswordChange).toBe(false);
    // 改密提升账号版本，旧会话必须失效；小程序也会回到登录页。
    const expired = await customer.get('/scm/mall/auth/profile');
    expect((await expired.json()).code).toBe(30007);
    await customer.dispose();
    const login = await call<Row>(anonymous, 'post', '/scm/mall/auth/login', {phone, password: newPassword});
    expect(login.profile.firstPasswordChange).toBe(false);
    customer = await apiClient(login.token);
    const addresses = await call<Row[]>(customer, 'post', '/scm/mall/addresses', {
      receiverName: '联调客户',
      receiverPhone: phone,
      provinceCode: 330000,
      provinceName: '浙江省',
      cityCode: 330100,
      cityName: '杭州市',
      districtCode: 330106,
      districtName: '西湖区',
      detailAddress: '联调收货地址',
      longitude: '120.13000000',
      latitude: '30.27000000',
      isDefault: true,
    });
    expect(addresses).toHaveLength(1);
    const input = {items: [{skuId, quantity: '2.0000'}], addressId: addresses[0].addressId, warehouseId: harness.warehouseId};
    const preview = await call<Row>(customer, 'post', '/scm/mall/orders/preview', input);
    expect(preview.unavailableSkuIds).toEqual([]);
    expect(Number(preview.estimatedAmount)).toBe(7);
    expect(preview.priceFingerprint).toBeTruthy();
    const submission = {...input, priceFingerprint: preview.priceFingerprint, remark: `${harness.runTag}商城串联`};
    const idempotencyKey = randomUUID();
    const submit = async () => {
      const response = await customer!.post('/scm/mall/orders', {data: submission, headers: {'Idempotency-Key': idempotencyKey}});
      const body = await response.json();
      expect(body.code, body.msg).toBe(0);
      return body.data as Row;
    };
    const order = await submit();
    const replay = await submit();
    expect(String(replay.orderId)).toBe(String(order.orderId));
    expect(order.orderSource).toBe('MALL');
    const detail = await call<Row>(harness.admin, 'get', `/scm/order/detail/${order.orderId}`);
    expect(String(detail.customerId)).toBe(customerId);
    expect(detail.orderSource).toBe('MALL');
    expect(detail.items).toHaveLength(1);
    expect(Number(detail.items[0].orderedQuantity)).toBe(2);
    expect(Number(detail.orderedTotalAmount)).toBe(7);
    const listed = await call<Row>(harness.admin, 'post', '/scm/order/query', {pageNum: 1, pageSize: 10, keyword: order.orderNo});
    expect(listed.total).toBe(1);
    expect(String(listed.list[0].orderId)).toBe(String(order.orderId));
    await authenticate(page, harness.adminToken);
    await page.goto('/#/order/order-list');
    await page.getByPlaceholder('名称或订单号').fill(order.orderNo);
    await page.getByPlaceholder('名称或订单号').press('Enter');
    await expect(page.locator('#order-table tr').filter({hasText: order.orderNo})).toHaveCount(1);
    await page.screenshot({path: '../.runtime/mall-order-admin-chain.png', fullPage: true});
  } finally {
    if (customer) {
      await customer.post('/scm/mall/auth/logout').catch(() => undefined);
      await customer.dispose();
    }
    await anonymous.dispose();
    await harness.close();
  }
});
