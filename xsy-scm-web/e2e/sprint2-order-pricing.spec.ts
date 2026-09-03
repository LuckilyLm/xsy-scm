import { expect, test } from '@playwright/test';
import type { APIRequestContext } from '@playwright/test';

const apiBase = process.env.XSY_API_BASE_URL ?? 'http://127.0.0.1:8080/api';

type Envelope<T> = { code: number; message: string; data: T };
type Product = {
  id: number;
  skus: Array<{
    id: number;
    skuCode: string;
    productType: 'STANDARD' | 'NON_STANDARD';
  }>;
};
type CustomerType = { id: number; status: 'ENABLED' | 'DISABLED' };
type Order = {
  id: number;
  version: number;
  status: 'DRAFT' | 'PENDING' | 'CONFIRMED';
  totalAmount: string;
  items: Array<{
    id: number;
    version: number;
    lockedUnitPrice: string | null;
    priceSource: 'AGREEMENT' | 'MARKET' | 'OVERRIDE';
  }>;
};

const unique = (prefix: string) =>
  `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

async function api<T>(
  request: APIRequestContext,
  method: 'get' | 'post',
  path: string,
  options?: Parameters<APIRequestContext['post']>[1],
): Promise<T> {
  const response = await request[method](`${apiBase}${path}`, options);
  expect(response.status(), `${method.toUpperCase()} ${path}: ${await response.text()}`).toBe(200);
  const body = (await response.json()) as Envelope<T>;
  expect(body.code).toBe(0);
  return body.data;
}

async function getDemoNonStandardSku(request: APIRequestContext) {
  const page = await api<{ records: Product[] }>(request, 'get', '/products', {
    params: { page: 1, pageSize: 20, keyword: 'SKU-APPLE-JIN' },
  });
  const product = page.records.find((entry) =>
    entry.skus.some((sku) => sku.skuCode === 'SKU-APPLE-JIN'),
  );
  expect(product).toBeDefined();
  const sku = product!.skus.find((entry) => entry.skuCode === 'SKU-APPLE-JIN');
  expect(sku?.productType).toBe('NON_STANDARD');
  return sku!;
}

async function getOrder(request: APIRequestContext, orderId: number) {
  return api<Order>(request, 'get', `/orders/${orderId}`);
}

test('allowlisted agreement price locks on submit and manual quantity confirms the order', async ({
  page,
  request,
}) => {
  const sku = await getDemoNonStandardSku(request);
  const customerTypes = await api<CustomerType[]>(request, 'get', '/customer-types');
  const customerType = customerTypes.find((entry) => entry.status === 'ENABLED');
  expect(customerType).toBeDefined();

  const customerCode = unique('E2E-CUST-PRICE');
  const customerName = `协议价验收客户-${customerCode.slice(-12)}`;
  const customerId = await api<number>(request, 'post', '/customers', {
    data: {
      version: null,
      customerCode,
      name: customerName,
      customerTypeId: customerType!.id,
      status: 'ENABLED',
      visibilityPolicy: 'ALLOWLIST',
      visibilities: [{ id: null, version: null, skuId: sku.id }],
    },
  });

  const orderableSkus = await api<Array<{ id: number; skuCode: string }>>(
    request,
    'get',
    `/customers/${customerId}/skus`,
  );
  expect(orderableSkus).toEqual([
    expect.objectContaining({ id: sku.id, skuCode: sku.skuCode }),
  ]);

  const agreementPrice = '5.2500';
  const agreementId = await api<number>(request, 'post', '/customer-agreement-prices', {
    data: {
      version: null,
      customerId,
      skuId: sku.id,
      unitPrice: agreementPrice,
      effectiveFrom: new Date(Date.now() - 60_000).toISOString(),
      effectiveTo: null,
    },
  });
  expect(agreementId).toBeGreaterThan(0);

  const orderId = await api<number>(request, 'post', '/orders', {
    headers: { 'Idempotency-Key': unique('order-create') },
    data: {
      version: null,
      customerId,
      source: 'NORMAL',
      originalOrderId: null,
      supplementReason: null,
      items: [{
        id: null,
        version: null,
        skuId: sku.id,
        orderedQuantity: '3.0000',
        unitPrice: null,
        manualPriceOverride: false,
        overrideReason: null,
      }],
    },
  });

  const draft = await getOrder(request, orderId);
  expect(draft.status).toBe('DRAFT');
  expect(draft.items[0].priceSource).toBe('AGREEMENT');

  const submitted = await api<Order>(request, 'post', `/orders/${orderId}/submit`, {
    headers: { 'Idempotency-Key': unique('order-submit') },
    data: { version: draft.version },
  });
  expect(submitted.status).toBe('PENDING');
  expect(submitted.items[0]).toMatchObject({
    lockedUnitPrice: agreementPrice,
    priceSource: 'AGREEMENT',
  });

  await page.goto(`/orders/${orderId}`);
  await expect(page.getByText(customerName)).toBeVisible();
  await expect(page.getByText('协议价', { exact: true })).toBeVisible();
  await expect(page.getByText(`¥ ${agreementPrice}`)).toBeVisible();
  await expect(page.getByText('待录入')).toBeVisible();

  await page.getByRole('button', { name: '录入实重' }).click();
  const weightDialog = page.getByRole('dialog', { name: /录入 .* 实重/ });
  await weightDialog.getByRole('textbox').first().fill('2.5000');
  await weightDialog.getByPlaceholder('说明称重或修正原因').fill('Playwright 手工称重验收');
  await weightDialog.getByRole('button', { name: '确认录入' }).click();

  await expect(page.getByText('2.5000 斤')).toBeVisible();
  const confirmButton = page.getByRole('button', { name: '确认订单' });
  await expect(confirmButton).toBeEnabled();
  await confirmButton.click();
  await expect(page.getByText('已确认', { exact: true })).toBeVisible();
  await expect(page.getByText('¥ 13.1250').first()).toBeVisible();

  await page.getByRole('tab', { name: '操作日志' }).click();
  await expect(page.getByText('ACTUAL_QUANTITY', { exact: true })).toBeVisible();
  await expect(page.getByText('CONFIRM', { exact: true })).toBeVisible();

  const confirmed = await getOrder(request, orderId);
  expect(confirmed).toMatchObject({ status: 'CONFIRMED', totalAmount: '13.1250' });
});
