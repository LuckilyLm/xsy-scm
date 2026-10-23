import {getRequest, postDownload, postRequest, request, type RequestOptions} from '/@/lib/axios';
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

/** 幂等命令：失败保留原 UUID 供重试，成功或载荷变化后换新键。需求 / 收货 API 从这里复用。 */
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
  query: (data: OrderQuery, options?: RequestOptions) =>
    postRequest('/scm/purchase/query', data, options) as unknown as Promise<ScmResponse<ScmPage<Order>>>,
  detail: (id: Id, options?: RequestOptions) => getRequest(`/scm/purchase/detail/${id}`, {}, options) as unknown as Promise<ScmResponse<Order>>,
  /** 单张采购单的操作日志（按 `created_at DESC` 返回，最新在前）。 */
  logs: (orderId: Id, options?: RequestOptions) =>
    getRequest(`/scm/purchase/log/${orderId}`, {}, options) as unknown as Promise<ScmResponse<LogRow[]>>,
  /** 采购单导出：按当前筛选和勾选列生成文件，不改变单据状态。 */
  export: (data: OrderExportPayload) => postDownload('/scm/purchase/export', data),

  create: (data: OrderPayload) => purchaseCommand<Order>('/scm/purchase/create', data),
  update: (data: OrderPayload) => postRequest('/scm/purchase/update', data) as unknown as Promise<ScmResponse<Order>>,
  /** 改派采购归属：独立权限；/update 不改归属，重复提交由版本号拦截。 */
  reassign: (data: OrderReassignPayload) => postRequest('/scm/purchase/reassign', data) as unknown as Promise<ScmResponse<Order>>,
  submit: (data: OrderVersionPayload) => purchaseCommand<Order>('/scm/purchase/submit', data),
  cancel: (data: OrderCancelPayload) => purchaseCommand<Order>('/scm/purchase/cancel', data),
  shortClose: (data: OrderShortClosePayload) => purchaseCommand<Order>('/scm/purchase/short-close', data),
  /** 批量少收关单：整批原子、逐单校验。 */
  batchShortClose: (data: OrderBatchShortClosePayload) =>
    postRequest('/scm/purchase/batch/short-close', data) as unknown as Promise<ScmResponse<string>>,
  delete: (data: OrderVersionPayload) => postRequest('/scm/purchase/delete', data) as unknown as Promise<ScmResponse<string>>,
  batchDelete: (orders: OrderVersionPayload[]) => postRequest('/scm/purchase/batch-delete', {orders}) as unknown as Promise<ScmResponse<string>>,
};

export default purchaseOrderApi;
