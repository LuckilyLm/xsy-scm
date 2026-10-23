/** 采购单二维码分享：生成、撤销、查询。令牌明文只在创建时返回一次，前端不缓存。 */
import {getRequest, postRequest} from '/@/lib/axios';
import type {Id} from '/@/views/business/scm/inventory/inventory-types';
export interface PurchaseShare {
  id: Id;
  purchaseOrderId: Id;
  token?: string;
  tokenPrefix: string;
  expiresAt: string;
  revokedAt?: string | null;
  lastUsedAt?: string | null;
  useCount: number;
  createdBy: string;
  createdAt: string;
}
export const purchaseShareApi = {
  feedback: (purchaseOrderId: Id, beforeId?: Id) => getRequest<PurchaseShareFeedback[]>('/scm/purchase/share/feedback', {purchaseOrderId, beforeId}),
  /** 生成分享链接；同一张单上的旧链接当场失效。 */
  create: (purchaseOrderId: Id, validDays?: number) => postRequest<PurchaseShare>('/scm/purchase/share/create', {purchaseOrderId, validDays}),
  revoke: (id: Id) => postRequest<string>('/scm/purchase/share/revoke', {id}),
  list: (purchaseOrderId: Id) => getRequest<PurchaseShare[]>('/scm/purchase/share/list', {purchaseOrderId}),
};

export interface PurchaseShareFeedback {
  id: Id;
  purchaseOrderItemId: Id;
  productName: string;
  skuName: string;
  purchaseUnit: string;
  unitPrice?: string | null;
  receivedQuantity?: string | null;
  remark?: string | null;
  submittedAt: string;
}
