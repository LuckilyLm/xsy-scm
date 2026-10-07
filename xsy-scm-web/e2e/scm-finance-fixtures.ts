/* Shared real-account and business-fact setup for Finance R1 browser acceptance. */
import {randomUUID} from 'node:crypto';
import type {APIRequestContext} from '@playwright/test';
import {
    apiClient,
    login,
    provisionTempAccounts,
    type TempAccounts,
} from './scm-e2e-account';
import {createLocatedCustomer, createSku} from './scm-delivery-fixtures';

export type Row = Record<string, any>;
export type FinanceHarness = {
    accounts: TempAccounts;
    adminToken: string;
    financeToken: string;
    salesToken: string;
    admin: APIRequestContext;
    finance: APIRequestContext;
    sales: APIRequestContext;
    runTag: string;
    warehouseId: number;
    close: () => Promise<void>;
};

export type ReceivableLineInput = {
    quantity: string;
    sortedQuantity?: string;
    sortedResult?: 'NORMAL' | 'SHORT';
};

export type SignedOrderFacts = {
    customerId: string;
    customerName: string;
    supplierId: string;
    orderId: string;
    orderNo: string;
    receivable: Row;
    receivableDetail: Row;
    orderDetail: Row;
    skuIds: string[];
};

export function financeDateRange() {
    const today = new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai'}).format(new Date());
    const year = today.slice(0, 4);
    return {startDate: `${year}-01-01`, endDate: `${year}-12-31`};
}

type CallResult<T = Row> = {code: number; msg: string; data?: T; status: number};

export async function call<T = Row>(
    client: APIRequestContext,
    method: 'get' | 'post' | 'put',
    path: string,
    data?: unknown,
): Promise<T> {
    const response = method === 'get'
        ? await client.get(path)
        : await client[method](path, {data, headers: {'Idempotency-Key': randomUUID()}});
    const body = await response.json();
    if (body.code !== 0) throw new Error(`${method.toUpperCase()} ${path} failed: ${body.code} ${body.msg}`);
    return body.data as T;
}

export async function envelope(
    client: APIRequestContext,
    method: 'get' | 'post' | 'put',
    path: string,
    data?: unknown,
): Promise<CallResult> {
    const response = method === 'get'
        ? await client.get(path)
        : await client[method](path, {data, headers: {'Idempotency-Key': randomUUID()}});
    const body = await response.json().catch(() => ({}));
    return {code: Number(body.code), msg: String(body.msg ?? ''), data: body.data, status: response.status()};
}

export async function openFinanceHarness(): Promise<FinanceHarness> {
    const accounts = provisionTempAccounts('w8', undefined, ['SCM_FINANCE', 'SCM_SALES']);
    const runTag = `F1${Date.now().toString(36).toUpperCase()}`;
    let adminToken = '';
    let financeToken = '';
    let salesToken = '';
    let admin: APIRequestContext | undefined;
    let finance: APIRequestContext | undefined;
    let sales: APIRequestContext | undefined;
    try {
        adminToken = await login(accounts, accounts.admin);
        financeToken = await login(accounts, accounts.roleAccounts.SCM_FINANCE);
        salesToken = await login(accounts, accounts.roleAccounts.SCM_SALES);
        admin = await apiClient(adminToken);
        finance = await apiClient(financeToken);
        sales = await apiClient(salesToken);

        const options = await call<Row[]>(admin, 'get', '/scm/delivery/options/warehouses');
        if (!options.length) throw new Error('No enabled warehouse is available for Finance E2E fixtures.');
        const warehouse = options.find((item) => item.warehouseCode === 'WH001') ?? options[0];
        const warehouseId = Number(warehouse.id);
        const details = await call<Row>(admin, 'get', `/scm/warehouse/detail/${warehouseId}`);
        if (details.longitude == null || details.latitude == null) {
            await call(admin, 'post', '/scm/warehouse/update', {
                id: warehouseId,
                version: details.version,
                warehouseCode: warehouse.warehouseCode,
                name: warehouse.name,
                address: details.address ?? `${runTag} 验收仓`,
                longitude: '113.94000000',
                latitude: '22.54000000',
                geomCrs: 'GCJ02',
                remark: details.remark ?? `${runTag} Finance E2E`,
            });
        }

        return {
            accounts, adminToken, financeToken, salesToken, admin, finance, sales, runTag, warehouseId,
            close: async () => {
                for (const client of [sales, finance, admin]) {
                    if (!client) continue;
                    await client.get('/login/logout').catch(() => undefined);
                    await client.dispose();
                }
                accounts.cleanup();
            },
        };
    } catch (error) {
        for (const client of [sales, finance, admin]) {
            if (client) await client.dispose().catch(() => undefined);
        }
        accounts.cleanup();
        throw error;
    }
}

async function createSupplier(harness: FinanceHarness, suffix: string, skuIds: string[]) {
    // 供应商编码由服务端生成（SUP + 6 位序号），创建载荷不再提交编码；后续步骤按 supplierId 使用。
    const supplierId = String(await call(harness.admin, 'post', '/scm/supplier/add', {
        name: `${harness.runTag}供应商${suffix}`,
    }));
    await call(harness.admin, 'post', '/scm/supplier/sku/replace', {
        supplierId,
        items: skuIds.map((skuId) => ({skuId: Number(skuId), purchaseUnit: 'kg', defaultFlag: true, status: 'ENABLED'})),
    });
    return supplierId;
}

async function stockIn(harness: FinanceHarness, supplierId: string, skuIds: string[], quantities: string[]) {
    const order = await call<Row>(harness.admin, 'post', '/scm/purchase/create', {
        supplierId,
        warehouseId: harness.warehouseId,
        purchaserId: null,
        plannedArrivalDate: null,
        remark: `${harness.runTag} Finance E2E 备货`,
        items: skuIds.map((skuId, index) => ({
            skuId: Number(skuId), quantity: quantities[index], price: '6.2000', allocations: [],
        })),
    });
    await call(harness.admin, 'post', '/scm/purchase/submit', {id: order.id, version: order.version});
    const receipt = await call<Row>(harness.admin, 'post', '/scm/purchase/receipt/create', {
        purchaseOrderId: order.id,
        receiptMode: 'DIRECT',
        remark: `${harness.runTag} Finance E2E 备货收货`,
    });
    const items = (receipt.items as Row[]).map((item, index) => ({
        receiptItemId: item.id,
        version: item.version,
        receivedQuantity: quantities[index],
        actualWeight: quantities[index],
        weightSource: 'MANUAL',
    }));
    return call(harness.admin, 'post', '/scm/purchase/receipt/confirm', {
        id: receipt.id, version: receipt.version, items,
    });
}

/**
 * 订单行/分拣行都按 skuId 认，不按数组位置认：满赠会在分拣任务里追加赠品权益行，
 * 位置匹配会把赠品当成第 N 条采购行（D-41）。
 */
function pickOrderLine(detail: Row, skuId: string, tag: string): Row {
    const line = (detail.items as Row[]).find((item) => Number(item.skuId) === Number(skuId));
    if (!line) throw new Error(`${tag} 订单里没有 sku ${skuId} 的行`);
    return line;
}

function inputForSku(inputs: ReceivableLineInput[], skus: string[], skuId: unknown, tag: string) {
    const index = skus.findIndex((sku) => Number(sku) === Number(skuId));
    if (index < 0) throw new Error(`${tag} 分拣行 sku ${String(skuId)} 不在夹具建单清单里`);
    return inputs[index];
}

/**
 * 单独给某条 sku 在夹具仓补库存：满赠的赠品不属于任何订单行，
 * 没人买它就只能靠这条入口备货，否则发车时赠品出库会因无货而失败。
 */
export async function stockSku(harness: FinanceHarness, suffix: string, skuId: string, quantity: string) {
    const supplierId = await createSupplier(harness, suffix, [skuId]);
    return stockIn(harness, supplierId, [skuId], [quantity]);
}

export async function createSignedOrder(
    harness: FinanceHarness,
    suffix: string,
    lineInputs: ReceivableLineInput[],
    existingCustomerId?: string,
    confirmExtras?: {couponInstanceId?: number},
): Promise<SignedOrderFacts> {
    const address = `${harness.runTag}${suffix} 验收路`;
    const customerId = existingCustomerId ??
        await createLocatedCustomer(harness.admin, harness.runTag, suffix, address);
    const skus = await Promise.all(lineInputs.map((_, index) =>
        createSku(harness.admin, harness.runTag, `${suffix}${String.fromCharCode(65 + index)}`)));
    const supplierId = await createSupplier(harness, suffix, skus);
    const stockQuantities = lineInputs.map((line) => {
        const planned = Number(line.quantity);
        const picked = Number(line.sortedQuantity ?? line.quantity);
        return (Math.ceil(Math.max(planned, picked)) + 20).toFixed(4);
    });
    await stockIn(harness, supplierId, skus, stockQuantities);

    let order = await call<Row>(harness.admin, 'post', '/scm/order/create', {
        customerId,
        orderSource: 'ADMIN',
        address: {receiverName: `${harness.runTag}收货人`, receiverPhone: '13800000000', address},
        remark: `${harness.runTag} ${suffix} Finance E2E`,
        items: skus.map((skuId, index) => ({
            skuId,
            orderedQuantity: lineInputs[index].quantity,
            manualPriceOverride: false,
        })),
    });
    order = await call<Row>(harness.admin, 'post', '/scm/order/submit', {orderId: order.orderId, version: order.version});
    for (const [index, input] of lineInputs.entries()) {
        const fresh = await call<Row>(harness.admin, 'get', `/scm/order/detail/${order.orderId}`);
        const line = pickOrderLine(fresh, skus[index], `${harness.runTag} ${suffix}`);
        await call(harness.admin, 'post', '/scm/order/item/actual-quantity', {
            orderId: fresh.orderId,
            itemId: line.itemId,
            version: line.version,
            actualQuantity: input.quantity,
            reason: `${harness.runTag} Finance E2E 实重`,
        });
    }
    let orderDetail = await call<Row>(harness.admin, 'get', `/scm/order/detail/${order.orderId}`);
    order = await call<Row>(harness.admin, 'post', '/scm/order/confirm', {
        orderId: orderDetail.orderId,
        version: orderDetail.version,
        // 集团/独立结算链不传这个；活动券链要带 couponInstanceId 才能验「确认时冻结优惠」
        ...(confirmExtras ?? {}),
    });
    orderDetail = await call<Row>(harness.admin, 'get', `/scm/order/detail/${order.orderId}`);

    const sorting = await call<Row>(harness.admin, 'post', '/scm/sorting/tasks', {
        warehouseId: harness.warehouseId,
        salesOrderItemIds: (orderDetail.items as Row[]).map((item) => Number(item.itemId)),
        remark: `${harness.runTag} ${suffix} Finance E2E 分拣`,
    });
    await call(harness.admin, 'post', `/scm/sorting/tasks/${sorting.task.id}/entry`, {
        // 满赠任务的 items 是「订单行 + 赠品权益行」的合并视图，赠品行追加在订单行之后。
        // 按 index 取 lineInputs 会在有赠品时错位（D-41），所以只提交订单行并按 skuId 认行。
        items: (sorting.items as Row[])
            .filter((item) => item.sourceType !== 'PROMOTION_GIFT')
            .map((item) => {
                const input = inputForSku(lineInputs, skus, item.skuId, `${harness.runTag} ${suffix}`);
                return {
                    id: item.id,
                    version: item.version,
                    sortedQuantity: input.sortedQuantity ?? input.quantity,
                    result: input.sortedResult ?? 'NORMAL',
                    ...(input.sortedResult === 'SHORT' ? {reason: `${harness.runTag} 少拣`} : {}),
                };
            }),
    });
    const sortingReady = await call<Row>(harness.admin, 'get', `/scm/sorting/tasks/${sorting.task.id}`);
    await call(harness.admin, 'post', `/scm/sorting/tasks/${sorting.task.id}/complete`, {
        version: sortingReady.task.version,
    });

    const routeId = String(await call(harness.admin, 'post', '/scm/delivery/routes', {
        routeName: `${harness.runTag} ${suffix} Finance E2E 线路`,
        deliveryDate: new Intl.DateTimeFormat('sv-SE', {timeZone: 'Asia/Shanghai'}).format(new Date()),
        warehouseId: harness.warehouseId,
        remark: harness.runTag,
    }));
    let route = await call<Row>(harness.admin, 'get', `/scm/delivery/routes/${routeId}`);
    await call(harness.admin, 'post', `/scm/delivery/routes/${routeId}/orders`, {
        version: route.route.version,
        orderIds: [Number(order.orderId)],
        reason: `${harness.runTag} 组单`,
    });
    route = await call<Row>(harness.admin, 'get', `/scm/delivery/routes/${routeId}`);
    for (const stop of route.stops as Row[]) {
        if (stop.longitude != null && stop.latitude != null) continue;
        await call(harness.admin, 'put', `/scm/delivery/routes/${routeId}/stops/${stop.id}`, {
            version: route.route.version,
            longitude: '113.94600000',
            latitude: '22.55300000',
            geomCrs: 'GCJ02',
        });
        route = await call<Row>(harness.admin, 'get', `/scm/delivery/routes/${routeId}`);
    }
    await call(harness.admin, 'post', `/scm/delivery/routes/${routeId}/plan`, {
        version: route.route.version, reason: `${harness.runTag} 规划`,
    });
    route = await call<Row>(harness.admin, 'get', `/scm/delivery/routes/${routeId}`);
    await call(harness.admin, 'post', `/scm/delivery/routes/${routeId}/dispatch`, {version: route.route.version});
    route = await call<Row>(harness.admin, 'get', `/scm/delivery/routes/${routeId}`);
    const routeOrder = (route.orders as Row[]).find((item) => Number(item.orderId) === Number(order.orderId));
    if (!routeOrder) throw new Error(`Planned route ${routeId} is missing order ${order.orderId}.`);
    await call(harness.admin, 'post', `/scm/delivery/routes/${routeId}/orders/${order.orderId}/sign`, {
        version: routeOrder.version,
        result: 'SIGNED',
    });
    route = await call<Row>(harness.admin, 'get', `/scm/delivery/routes/${routeId}`);
    await call(harness.admin, 'post', `/scm/delivery/routes/${routeId}/complete`, {version: route.route.version});

    const rows = await call<Row>(harness.finance, 'post', '/scm/finance/receivable/query', {
        ...financeDateRange(), pageNum: 1, pageSize: 100, customerId,
    });
    const receivable = (rows.list as Row[]).find((item) => Number(item.orderId) === Number(order.orderId));
    if (!receivable) throw new Error(`Signed order ${order.orderNo} did not produce an in-scope Finance receivable.`);
    const receivableDetail = await call<Row>(harness.finance, 'get', `/scm/finance/receivable/${receivable.receivableId}`);
    orderDetail = await call<Row>(harness.admin, 'get', `/scm/order/detail/${order.orderId}`);
    const customer = await call<Row>(harness.admin, 'get', `/scm/customer/detail/${customerId}`);
    return {
        customerId, customerName: customer.name, supplierId,
        orderId: String(order.orderId), orderNo: String(order.orderNo),
        receivable, receivableDetail, orderDetail, skuIds: skus,
    };
}

export async function createReceipt(harness: FinanceHarness, customerId: string, amount: string, suffix: string) {
    return call<Row>(harness.finance, 'post', '/scm/finance/receipt/add', {
        customerId,
        amount,
        method: 'BANK_TRANSFER',
        receivedAt: new Date().toISOString(),
        externalReference: `${harness.runTag}-${suffix}`,
        remark: `${harness.runTag} ${suffix}`,
    });
}

export async function createWriteOff(
    harness: FinanceHarness,
    sourceId: number | string,
    targets: Array<{targetId: number | string; amount: string}>,
) {
    return call<Row>(harness.finance, 'post', '/scm/finance/write-off/add', {
        sourceType: 'RECEIPT', sourceId: Number(sourceId),
        items: targets.map((target) => ({targetId: Number(target.targetId), amount: target.amount})),
    });
}

export async function createApprovedReturn(
    harness: FinanceHarness,
    facts: SignedOrderFacts,
    quantity: string,
    suffix: string,
): Promise<{returnId: number; refund: Row; approved: Row}> {
    const firstItem = (facts.orderDetail.items as Row[])[0];
    const created = await call<Row>(harness.admin, 'post', '/scm/order/return/create', {
        orderId: Number(facts.orderId),
        reason: `${harness.runTag} ${suffix} 验收退货`,
        items: [{orderItemId: Number(firstItem.itemId), requestedQuantity: quantity}],
    });
    const approved = await call<Row>(harness.admin, 'post', '/scm/order/return/approve', {
        returnId: created.returnId,
        version: created.version,
        items: [{orderItemId: Number(firstItem.itemId), approvedQuantity: quantity}],
    });
    const refunds = await call<Row>(harness.admin, 'post', '/scm/order/refund/query', {
        pageNum: 1, pageSize: 100, orderId: Number(facts.orderId),
    });
    const refund = (refunds.list as Row[]).find((item) => Number(item.returnId) === Number(created.returnId));
    if (!refund) throw new Error(`Approved return ${created.returnNo} did not create a refund fact.`);
    return {returnId: Number(created.returnId), refund, approved};
}

export async function createPurchaseReceipt(
    harness: FinanceHarness,
    suffix: string,
    plannedQuantity: string,
    receivedQuantity: string,
    shortCloseRemainder = false,
): Promise<{supplierId: string; skuId: string; purchaseOrder: Row; receipt: Row; payables: Row[]}> {
    const skuId = await createSku(harness.admin, harness.runTag, `${suffix}P`);
    const supplierId = await createSupplier(harness, `${suffix}P`, [skuId]);
    const purchaseOrder = await call<Row>(harness.admin, 'post', '/scm/purchase/create', {
        supplierId,
        warehouseId: harness.warehouseId,
        purchaserId: null,
        plannedArrivalDate: null,
        remark: `${harness.runTag} ${suffix} Finance payable E2E`,
        items: [{skuId: Number(skuId), quantity: plannedQuantity, price: '6.2000', allocations: []}],
    });
    await call(harness.admin, 'post', '/scm/purchase/submit', {
        id: purchaseOrder.id, version: purchaseOrder.version,
    });
    const receipt = await call<Row>(harness.admin, 'post', '/scm/purchase/receipt/create', {
        purchaseOrderId: purchaseOrder.id,
        receiptMode: 'DIRECT',
        remark: `${harness.runTag} ${suffix} Finance payable receipt`,
    });
    const receiptItem = (receipt.items as Row[])[0];
    await call(harness.admin, 'post', '/scm/purchase/receipt/confirm', {
        id: receipt.id,
        version: receipt.version,
        items: [{
            receiptItemId: receiptItem.id,
            version: receiptItem.version,
            receivedQuantity,
            actualWeight: receivedQuantity,
            weightSource: 'MANUAL',
        }],
    });
    if (shortCloseRemainder) {
        const currentOrder = await call<Row>(harness.admin, 'get', `/scm/purchase/detail/${purchaseOrder.id}`);
        await call(harness.admin, 'post', '/scm/purchase/short-close', {
            id: purchaseOrder.id,
            version: currentOrder.version,
            shortCloseReason: `${harness.runTag} ${suffix} 少收关单`,
        });
    }
    const result = await call<Row>(harness.finance, 'post', '/scm/finance/payable/query', {
        ...financeDateRange(), pageNum: 1, pageSize: 100, supplierId: Number(supplierId),
    });
    return {supplierId, skuId, purchaseOrder, receipt, payables: result.list as Row[]};
}

export async function completeRefund(harness: FinanceHarness, refund: Row, suffix: string) {
    return call<Row>(harness.admin, 'post', '/scm/order/refund/complete', {
        refundId: Number(refund.refundId),
        version: refund.version,
        externalReference: `${harness.runTag}-${suffix}`,
    });
}
