import {getRequest, postDownload, postRequest, request} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    Id,
    LogRow,
    Order,
    OrderBatchShortClosePayload,
    OrderCancelPayload,
    OrderExportPayload,
    OrderPayload,
    OrderQuery,
    OrderReassignPayload,
    OrderShortClosePayload,
    OrderVersionPayload,
} from '/@/views/business/scm/purchase/purchase-types';

/**
 * 幂等命令封装。
 *
 * 失败的请求**保留**它原来的 UUID：同一个签名重试会复用同一个键，因此后端能识别成重放；
 * 一旦请求成功（或载荷变了）就换新键。这与 `order-api.ts` 的 `orderCommand` 同源。
 *
 * 放在本文件而不是独立模块：不新增共享文件；
 * 需求 / 收货 API 从这里 import。
 */
const keys = new Map<string, string>();

export async function purchaseCommand<T>(path: string, data: unknown): Promise<ScmResponse<T>> {
    const signature = path + JSON.stringify(data);
    let key = keys.get(signature);
    if (!key) {
        key = crypto.randomUUID();
        keys.set(signature, key);
    }
    const result = (await request({
        url: path,
        method: 'post',
        data,
        headers: {'Idempotency-Key': key},
    })) as unknown as ScmResponse<T>;
    keys.delete(signature);
    return result;
}

export const purchaseOrderApi = {
    query: (data: OrderQuery) =>
        postRequest('/scm/purchase/query', data) as unknown as Promise<ScmResponse<ScmPage<Order>>>,
    detail: (id: Id) =>
        getRequest(`/scm/purchase/detail/${id}`, {}) as unknown as Promise<ScmResponse<Order>>,
    /** 单张采购单的操作日志（按 `created_at DESC` 返回，**最新在前**）。 */
    logs: (orderId: Id) =>
        getRequest(`/scm/purchase/log/${orderId}`, {}) as unknown as Promise<ScmResponse<LogRow[]>>,
    /** 采购单列表导出（只读）：当前筛选 + 勾选列落 xlsx，绝不改变采购状态。 */
    export: (data: OrderExportPayload) => postDownload('/scm/purchase/export', data),

    create: (data: OrderPayload) => purchaseCommand<Order>('/scm/purchase/create', data),
    update: (data: OrderPayload) =>
        postRequest('/scm/purchase/update', data) as unknown as Promise<ScmResponse<Order>>,
    /**
     * 改派采购归属（{@code scm:purchase:assign}）：独立端点、独立权限，带乐观锁 version。
     * `/update` 永不改归属，改派只有这一条路；不接幂等头（与 `/update` 同一取向，重复提交由 id+version 挡）。
     */
    reassign: (data: OrderReassignPayload) =>
        postRequest('/scm/purchase/reassign', data) as unknown as Promise<ScmResponse<Order>>,
    submit: (data: OrderVersionPayload) => purchaseCommand<Order>('/scm/purchase/submit', data),
    cancel: (data: OrderCancelPayload) => purchaseCommand<Order>('/scm/purchase/cancel', data),
    shortClose: (data: OrderShortClosePayload) =>
        purchaseCommand<Order>('/scm/purchase/short-close', data),
    /** 批量少收关单：整批原子、逐单校验；后端不接幂等头，故同 batchDelete 走 postRequest。 */
    batchShortClose: (data: OrderBatchShortClosePayload) =>
        postRequest('/scm/purchase/batch/short-close', data) as unknown as Promise<ScmResponse<string>>,
    delete: (data: OrderVersionPayload) =>
        postRequest('/scm/purchase/delete', data) as unknown as Promise<ScmResponse<string>>,
    batchDelete: (orders: OrderVersionPayload[]) =>
        postRequest('/scm/purchase/batch-delete', {orders}) as unknown as Promise<ScmResponse<string>>,
};

export default purchaseOrderApi;
