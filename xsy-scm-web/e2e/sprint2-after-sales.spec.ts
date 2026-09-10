import {expect, test} from '@playwright/test';
import type {APIRequestContext} from '@playwright/test';
import {mutationHeaders} from './authenticated-api';

const apiBase = process.env.XSY_API_BASE_URL ?? 'http://127.0.0.1:8080/api';

type Envelope<T> = { code: number; message: string; data: T };
type Product = { skus: Array<{ id: number; skuCode: string }> };
type CustomerType = { id: number; status: 'ENABLED' | 'DISABLED' };
type Order = {
    id: number;
    version: number;
    orderNo: string;
    status: 'DRAFT' | 'PENDING' | 'CONFIRMED';
    source: 'NORMAL' | 'SUPPLEMENT';
    originalOrderId: number | null;
    supplementReason: string | null;
    items: Array<{
        id: number;
        version: number;
        actualQuantity: string | null;
        lockedUnitPrice: string | null;
    }>;
};
type OrderReturn = {
    id: number;
    version: number;
    status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
    approvedAmount: number | string | null;
    items: Array<{
        id: number;
        version: number;
        orderItemId: number;
        requestedQuantity: number | string;
        approvedQuantity: number | string | null;
    }>;
    refund: Refund | null;
};
type Refund = {
    id: number;
    version: number;
    status: 'PENDING' | 'COMPLETED';
    refundAmount: number | string;
    externalReference: string | null;
};

const unique = (prefix: string) =>
    `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

async function api<T>(
    request: APIRequestContext,
    method: 'get' | 'post',
    path: string,
    options?: Parameters<APIRequestContext['post']>[1],
): Promise<T> {
    const response = await request[method](`${apiBase}${path}`, method === 'post' ? {
        ...options,
        headers: await mutationHeaders(request, options?.headers as Record<string, string> | undefined),
    } : options);
    expect(response.status(), `${method.toUpperCase()} ${path}: ${await response.text()}`).toBe(200);
    const body = (await response.json()) as Envelope<T>;
    expect(body.code).toBe(0);
    return body.data;
}

async function getDemoSkuId(request: APIRequestContext) {
    const page = await api<{ records: Product[] }>(request, 'get', '/products', {
        params: {page: 1, pageSize: 20, keyword: 'SKU-APPLE-JIN'},
    });
    const sku = page.records.flatMap((product) => product.skus)
        .find((entry) => entry.skuCode === 'SKU-APPLE-JIN');
    expect(sku).toBeDefined();
    return sku!.id;
}

async function getOrder(request: APIRequestContext, orderId: number) {
    return api<Order>(request, 'get', `/orders/${orderId}`);
}

async function createAndConfirmOrder(
    request: APIRequestContext,
    customerId: number,
    skuId: number,
    source: 'NORMAL' | 'SUPPLEMENT',
    originalOrderId: number | null,
    orderedQuantity: string,
) {
    const supplementReason = source === 'SUPPLEMENT' ? 'Playwright 补单验收' : null;
    const orderId = await api<number>(request, 'post', '/orders', {
        headers: {'Idempotency-Key': unique('order-create')},
        data: {
            version: null,
            customerId,
            source,
            originalOrderId,
            supplementReason,
            items: [{
                id: null,
                version: null,
                skuId,
                orderedQuantity,
                unitPrice: null,
                manualPriceOverride: false,
                overrideReason: null,
            }],
        },
    });
    const draft = await getOrder(request, orderId);
    const pending = await api<Order>(request, 'post', `/orders/${orderId}/submit`, {
        headers: {'Idempotency-Key': unique('order-submit')},
        data: {version: draft.version},
    });
    await api<unknown>(
        request,
        'post',
        `/orders/${orderId}/items/${pending.items[0].id}/actual-quantity`,
        {
            headers: {'Idempotency-Key': unique('actual-quantity')},
            data: {
                version: pending.items[0].version,
                actualQuantity: orderedQuantity,
                reason: 'Playwright 售后流程准备',
            },
        },
    );
    const weighed = await getOrder(request, orderId);
    await api<unknown>(request, 'post', `/orders/${orderId}/confirm`, {
        headers: {'Idempotency-Key': unique('order-confirm')},
        data: {version: weighed.version},
    });
    return getOrder(request, orderId);
}

test('supplement order supports partial approval, refund completion, and rejects over-return', async ({
                                                                                                          page,
                                                                                                          request,
                                                                                                      }) => {
    const skuId = await getDemoSkuId(request);
    const customerTypes = await api<CustomerType[]>(request, 'get', '/customer-types');
    const customerType = customerTypes.find((entry) => entry.status === 'ENABLED');
    expect(customerType).toBeDefined();

    const customerCode = unique('E2E-CUST-AFTER');
    const customerId = await api<number>(request, 'post', '/customers', {
        data: {
            version: null,
            customerCode,
            name: `补单售后验收客户-${customerCode.slice(-12)}`,
            customerTypeId: customerType!.id,
            status: 'ENABLED',
            visibilityPolicy: 'ALLOWLIST',
            visibilities: [{id: null, version: null, skuId}],
        },
    });

    const original = await createAndConfirmOrder(
        request,
        customerId,
        skuId,
        'NORMAL',
        null,
        '4.0000',
    );
    const supplement = await createAndConfirmOrder(
        request,
        customerId,
        skuId,
        'SUPPLEMENT',
        original.id,
        '6.0000',
    );
    expect(supplement).toMatchObject({
        status: 'CONFIRMED',
        source: 'SUPPLEMENT',
        originalOrderId: original.id,
        supplementReason: 'Playwright 补单验收',
    });

    await page.goto(`/orders/${supplement.id}`);
    await expect(page.getByText(supplement.orderNo)).toBeVisible();
    await expect(page.getByText('补单', {exact: true})).toBeVisible();
    await expect(page.getByText('Playwright 补单验收')).toBeVisible();
    await expect(page.getByText('已确认')).toBeVisible();

    const returnId = await api<number>(request, 'post', '/order-returns', {
        headers: {'Idempotency-Key': unique('return-create')},
        data: {
            orderId: supplement.id,
            reason: 'Playwright 部分退货验收',
            items: [{
                orderItemId: supplement.items[0].id,
                requestedQuantity: '2.0000',
            }],
        },
    });
    const pendingReturn = await api<OrderReturn>(request, 'get', `/order-returns/${returnId}`);
    expect(pendingReturn.status).toBe('PENDING');
    expect(Number(pendingReturn.items[0].requestedQuantity)).toBe(2);

    const refundId = await api<number>(request, 'post', `/order-returns/${returnId}/approve`, {
        headers: {'Idempotency-Key': unique('return-approve')},
        data: {
            version: pendingReturn.version,
            items: [{
                returnItemId: pendingReturn.items[0].id,
                version: pendingReturn.items[0].version,
                approvedQuantity: '1.5000',
            }],
        },
    });
    const approvedReturn = await api<OrderReturn>(request, 'get', `/order-returns/${returnId}`);
    expect(approvedReturn.status).toBe('APPROVED');
    expect(Number(approvedReturn.items[0].approvedQuantity)).toBe(1.5);
    expect(approvedReturn.refund).toMatchObject({id: refundId, status: 'PENDING'});

    const externalReference = unique('E2E-REFUND');
    await api<unknown>(request, 'post', `/order-refunds/${refundId}/complete`, {
        headers: {'Idempotency-Key': unique('refund-complete')},
        data: {version: approvedReturn.refund!.version, externalReference},
    });
    const completedRefund = await api<Refund>(request, 'get', `/order-refunds/${refundId}`);
    expect(completedRefund).toMatchObject({
        status: 'COMPLETED',
        externalReference,
    });
    expect(Number(completedRefund.refundAmount)).toBeGreaterThan(0);

    const overReturnResponse = await request.post(`${apiBase}/order-returns`, {
        headers: await mutationHeaders(request, {
            'Idempotency-Key': unique('return-over-limit'),
        }),
        data: {
            orderId: supplement.id,
            reason: 'Playwright 超额退货校验',
            items: [{
                orderItemId: supplement.items[0].id,
                requestedQuantity: '5.0000',
            }],
        },
    });
    expect(overReturnResponse.status()).toBe(409);
    const conflict = (await overReturnResponse.json()) as Envelope<null>;
    expect(conflict.code).not.toBe(0);
    expect(conflict.message).toMatch(/退货|数量|可退|超出/);
});
