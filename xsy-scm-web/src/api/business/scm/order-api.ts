import {request, getRequest, postRequest, getDownload, type RequestOptions} from '/@/lib/axios';
import type {ScmResponse, ScmPage} from '/@/types/business/scm/customer';
import type {
  Order,
  Query,
  Id,
  ImportResult,
  LastPurchasePrice,
  RecentPrice,
  OrderPoolQuery,
  OrderPoolRow,
  OrderPoolAssignee,
} from '/@/views/business/scm/order/order-types';
import type {ResolveResult} from '/@/types/business/scm/pricing';
// 失败重试复用同一 UUID；载荷变化或成功后换新键。
const keys = new Map<string, string>();
const importKeys = new WeakMap<File, string>();

export async function orderCommand<T>(path: string, data: unknown): Promise<ScmResponse<T>> {
  const signature = path + JSON.stringify(data);
  let key = keys.get(signature);
  if (!key) {
    key = crypto.randomUUID();
    keys.set(signature, key);
  }
  const result = await request<T>({
    url: path,
    method: 'post',
    data,
    headers: {'Idempotency-Key': key},
  });
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
  const result = await request<ImportResult>({
    url: '/scm/order/import',
    method: 'post',
    data,
    headers: {'Idempotency-Key': key},
  });
  importKeys.delete(file);
  return result;
}

export interface PastePreviewRow {
  rowNumber: number;
  productName?: string;
  specName?: string;
  unit?: string;
  quantity?: string;
  rawUnitPrice?: string;
  remark?: string;
  skuId?: Id;
  skuCode?: string;
  matchedProductName?: string;
  matchedSpecName?: string;
  saleUnit?: string;
  productType?: string;
  resolvedUnitPrice?: string | null;
  priceSource?: string | null;
  manualPrice: boolean;
  matched: boolean;
  errors: Array<{rowNumber: number; column: string; code: string; message: string}>;
}

export interface PastePreview {
  totalRows: number;
  matchedRows: number;
  errorRows: number;
  rows: PastePreviewRow[];
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
  /** 明细行快照：页面展示商品与规格，不展示明细主键。 */
  productName?: string | null;
  skuName?: string | null;
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
  unassignedPool: (data: OrderPoolQuery, options?: RequestOptions) => postRequest<ScmPage<OrderPoolRow>>('/scm/order/pool/query', data, options),
  poolAssignees: (options?: RequestOptions) => getRequest<OrderPoolAssignee[]>('/scm/order/pool/assignees', {}, options),
  claimPoolOrder: (data: {orderId: Id; version: number}) => orderCommand<OrderPoolRow>('/scm/order/pool/claim', data),
  assignPoolOrder: (data: {orderId: Id; version: number; sellerId: number}) => orderCommand<OrderPoolRow>('/scm/order/pool/assign', data),
  query: (data: Query, options?: RequestOptions) => postRequest<ScmPage<Order>>('/scm/order/query', data, options),
  detail: (id: Id, options?: RequestOptions) => getRequest<Order>('/scm/order/detail/' + id, {}, options),
  create: (data: Order) => orderCommand<Order>('/scm/order/create', data),
  exchangeReplacement: (returnId: Id, options?: RequestOptions) => getRequest<Id | null>('/scm/order/exchange/replacement/' + returnId, {}, options),
  createExchange: (returnId: Id, order: Order) => orderCommand<Order>('/scm/order/exchange/create', {returnId, order}),
  receivedCorrections: (orderId: Id, options?: RequestOptions) =>
    getRequest<ReceivedCorrection[]>('/scm/order/received-correction/list/' + orderId, {}, options),
  receivedCorrectionCandidate: (orderId: Id, orderItemId: Id, options?: RequestOptions) =>
    getRequest<ReceivedCorrectionCandidate>(`/scm/order/received-correction/candidate/${orderId}/${orderItemId}`, {}, options),
  recordReceivedCorrection: (data: {orderId: Id; orderItemId: Id; expectedLastCorrectionId: Id; receivedQuantity: string; reason: string}) =>
    orderCommand<ReceivedCorrection>('/scm/order/received-correction/record', data),
  createAndProgress: (data: Order) => orderCommand<Order>('/scm/order/create-and-progress', data),
  downloadImportTemplate: () => getDownload('/scm/order/import/template', {}),
  importOrders,
  /** 整单粘贴预览：只匹配商品和解析价，不建单。 */
  pastePreview: (
    data: {
      customerId: Id;
      rows: Array<{
        productName?: string;
        specName?: string;
        unit?: string;
        quantity?: string;
        unitPrice?: string;
        remark?: string;
      }>;
    },
    options?: RequestOptions
  ) => postRequest<PastePreview>('/scm/order/import/paste-preview', data, options),
  update: (data: Order) => postRequest<Order>('/scm/order/update', data),
  reprice: (data: {orderId: Id; version: number; reason: string}) => postRequest<Order>('/scm/order/reprice', data),
  submit: (data: unknown) => orderCommand<Order>('/scm/order/submit', data),
  confirm: (data: unknown) => orderCommand<Order>('/scm/order/confirm', data),
  creditCheck: (customerId: Id, requestedAmount?: string) =>
    getRequest<{
      allowed: boolean;
      overLimit: boolean;
      overdue: boolean;
      creditLimit: string;
      projectedExposure: string;
      amountThresholdHint?: string | null;
    }>('/scm/order/credit-check/' + customerId, {requestedAmount}),
  orderCreditCheck: (orderId: Id, options?: RequestOptions) => getRequest<CreditCheck>('/scm/order/credit-check/order/' + orderId, {}, options),
  cancel: (data: unknown) => orderCommand<Order>('/scm/order/cancel', data),
  actual: (data: unknown) => orderCommand<Order>('/scm/order/item/actual-quantity', data),
  delete: (data: unknown) => postRequest('/scm/order/delete', data),
  batchDelete: (orders: unknown[]) => postRequest('/scm/order/batch-delete', {orders}),
  preview: (
    data: {
      customerId: Id;
      skuIds: Id[];
      warehouseId?: Id | null;
    },
    options?: RequestOptions
  ) => postRequest<ResolveResult>('/scm/order/price/preview', data, options),
  /** 为已确认订单预留库存：货到后由业务人员执行；任一行可用量不足则整体失败。 */
  reserveStock: (orderId: Id) => postRequest<string>('/scm/order/reserve-stock/' + orderId, {}),
  /** 锁单/解锁：锁不是订单状态，只是改单需要额外授权的开关。 */
  lock: (data: {orderId: Id; version: number; reason?: string}) => postRequest<Order>('/scm/order/lock', data),
  unlock: (data: {orderId: Id; version: number; reason?: string}) => postRequest<Order>('/scm/order/unlock', data),
  /** 最近成交参考价：只作录单参考，不参与定价；limit 取 1~10，默认 5。 */
  recentPrices: (customerId: Id, skuId: Id, limit = 5, options?: RequestOptions) =>
    getRequest<RecentPrice[]>('/scm/order/reference/recent-prices', {customerId, skuId, limit}, options),
  /** 最近采购参考价：只读查询，一次最多 200 个商品规格；单次按逗号传递，避免数组参数序列化差异。 */
  lastPurchasePrices: (skuIds: Id[], options?: RequestOptions) =>
    getRequest<LastPurchasePrice[]>('/scm/order/reference/last-purchase-prices', {skuIds: skuIds.join(',')}, options),
};
