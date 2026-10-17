import {request, getRequest, postRequest, getDownload, type RequestOptions} from '/@/lib/axios';
import type {ScmResponse, ScmPage} from '/@/types/business/scm/customer';
import type {Order, Query, Id, ImportResult, RecentPrice, OrderPoolQuery, OrderPoolRow, OrderPoolAssignee} from '/@/views/business/scm/order/order-types';
import type {ResolveResult} from '/@/types/business/scm/pricing';
// A failed retry retains its UUID; a changed payload or successful request starts a new command.
const keys = new Map<string, string>();
const importKeys = new WeakMap<File, string>();

export async function orderCommand<T>(path: string, data: unknown): Promise<ScmResponse<T>> {
    const signature = path + JSON.stringify(data);
    let key = keys.get(signature);
    if (!key) {
        key = crypto.randomUUID();
        keys.set(signature, key);
    }
    const result = await request({
        url: path,
        method: 'post',
        data,
        headers: {'Idempotency-Key': key}
    }) as unknown as ScmResponse<T>;
    keys.delete(signature);
    return result;
}

async function importOrders(file: File): Promise<ScmResponse<ImportResult>> {
    const data = new FormData();
    data.append('file', file);
    let key = importKeys.get(file);
    if (!key) {
        key = crypto.randomUUID();
        importKeys.set(file, key);
    }
    const result = await request({
        url: '/scm/order/import',
        method: 'post',
        data,
        headers: {'Idempotency-Key': key}
    }) as unknown as ScmResponse<ImportResult>;
    importKeys.delete(file);
    return result;
}

export interface CreditCheck {
    allowed: boolean;
    overLimit: boolean;
    overdue: boolean;
    creditLimit: string;
    openReceivableAmount: string;
    confirmedOrderAmount: string;
    requestedOrderAmount: string;
    projectedExposure: string;
    earliestOverdueDate?: string | null;
    amountThresholdHint?: string | null;
}

export interface ReceivedCorrection {
    id: Id;
    orderId: Id;
    orderItemId: Id;
    saleUnitSnapshot: string;
    shippedQuantity: string;
    previousReceivedQuantity: string;
    receivedQuantity: string;
    reason: string;
    createdBy: string;
    createdAt: string;
}
export interface ReceivedCorrectionCandidate {
    orderItemId: Id;
    lastCorrectionId: Id;
    saleUnit: string;
    shippedQuantity: string;
    currentReceivedQuantity: string;
}

export const orderApi = {
    unassignedPool: (data: OrderPoolQuery, options?: RequestOptions) =>
        postRequest('/scm/order/pool/query', data, options) as unknown as Promise<ScmResponse<ScmPage<OrderPoolRow>>>,
    poolAssignees: () =>
        getRequest('/scm/order/pool/assignees', {}) as unknown as Promise<ScmResponse<OrderPoolAssignee[]>>,
    claimPoolOrder: (data: {orderId: Id; version: number}) =>
        orderCommand<OrderPoolRow>('/scm/order/pool/claim', data),
    assignPoolOrder: (data: {orderId: Id; version: number; sellerId: number}) =>
        orderCommand<OrderPoolRow>('/scm/order/pool/assign', data),
    query: (data: Query, options?: RequestOptions) =>
        postRequest('/scm/order/query', data, options) as unknown as Promise<ScmResponse<ScmPage<Order>>>,
    detail: (id: Id, options?: RequestOptions) =>
        getRequest('/scm/order/detail/' + id, {}, options) as unknown as Promise<ScmResponse<Order>>,
    create: (data: Order) => orderCommand<Order>('/scm/order/create', data),
    exchangeReplacement: (returnId: Id) =>
        getRequest('/scm/order/exchange/replacement/' + returnId, {}) as unknown as Promise<ScmResponse<Id | null>>,
    createExchange: (returnId: Id, order: Order) =>
        orderCommand<Order>('/scm/order/exchange/create', {returnId, order}),
    receivedCorrections: (orderId: Id, options?: RequestOptions) =>
        getRequest('/scm/order/received-correction/list/' + orderId, {}, options) as unknown as Promise<ScmResponse<ReceivedCorrection[]>>,
    receivedCorrectionCandidate: (orderId: Id, orderItemId: Id, options?: RequestOptions) =>
        getRequest(`/scm/order/received-correction/candidate/${orderId}/${orderItemId}`, {}, options) as unknown as Promise<ScmResponse<ReceivedCorrectionCandidate>>,
    recordReceivedCorrection: (data: {orderId: Id; orderItemId: Id; expectedLastCorrectionId: Id;
        receivedQuantity: string; reason: string}) =>
        orderCommand<ReceivedCorrection>('/scm/order/received-correction/record', data),
    createAndProgress: (data: Order) => orderCommand<Order>('/scm/order/create-and-progress', data),
    downloadImportTemplate: () => getDownload('/scm/order/import/template', {}),
    importOrders,
    update: (data: Order) => postRequest('/scm/order/update', data) as unknown as Promise<ScmResponse<Order>>,
    reprice: (data: {orderId: Id; version: number; reason: string}) =>
        postRequest('/scm/order/reprice', data) as unknown as Promise<ScmResponse<Order>>,
    submit: (data: unknown) => orderCommand<Order>('/scm/order/submit', data),
    confirm: (data: unknown) => orderCommand<Order>('/scm/order/confirm', data),
    creditCheck: (customerId: Id, requestedAmount?: string) => getRequest('/scm/order/credit-check/' + customerId, {requestedAmount}) as unknown as Promise<ScmResponse<{allowed: boolean; overLimit: boolean; overdue: boolean; creditLimit: string; projectedExposure: string; amountThresholdHint?: string | null}>>,
    orderCreditCheck: (orderId: Id, options?: RequestOptions) =>
        getRequest('/scm/order/credit-check/order/' + orderId, {}, options) as unknown as Promise<ScmResponse<CreditCheck>>,
    cancel: (data: unknown) => orderCommand<Order>('/scm/order/cancel', data),
    actual: (data: unknown) => orderCommand<Order>('/scm/order/item/actual-quantity', data),
    delete: (data: unknown) => postRequest('/scm/order/delete', data),
    batchDelete: (orders: unknown[]) => postRequest('/scm/order/batch-delete', {orders}),
    preview: (data: {
        customerId: Id;
        skuIds: Id[];
        warehouseId?: Id | null
    }, options?: RequestOptions) =>
        postRequest('/scm/order/price/preview', data, options) as unknown as Promise<ScmResponse<ResolveResult>>,
    /**
     * 为已确认订单预留库存。
     *
     * 显式操作而非确认时自动预留：本业务的库存在订单确认之后才产生，
     * 挂在确认上会让「先接单 → 再采购」链路无法运转。货到后由业务人员执行。
     * 严格语义：任一行可用量不足则整体失败（41011），不会只占一半。
     */
    reserveStock: (orderId: Id) => postRequest('/scm/order/reserve-stock/' + orderId, {}) as unknown as Promise<ScmResponse<string>>,
    /**
     * 某客户某 SKU 的最近成交参考价（只读）：仅录单旁证，不回算当前价格、不参与定价。
     * limit 由后端裁剪到 [1,10]，默认 5。
     */
    recentPrices: (customerId: Id, skuId: Id, limit = 5) => getRequest('/scm/order/reference/recent-prices', {customerId, skuId, limit}) as unknown as Promise<ScmResponse<RecentPrice[]>>,
};
