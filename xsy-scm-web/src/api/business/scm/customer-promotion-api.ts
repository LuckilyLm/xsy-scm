/** 客户推广二维码：生成、撤销、扫码记录。扫码只记录来源，不改归属。 */
import {getRequest, postRequest} from '/@/lib/axios';
import type {Id} from '/@/views/business/scm/inventory/inventory-types';
export interface CustomerPromotion {
  id: Id;
  customerId: Id;
  customerName?: string | null;
  code: string;
  sellerId?: Id | null;
  sellerName?: string | null;
  revokedAt?: string | null;
  scanCount?: number | null;
  createdBy: string;
  createdAt: string;
}
export interface CustomerPromotionScan {
  id: Id;
  scannedAt: string;
  sellerId?: Id | null;
  sellerName?: string | null;
  clientIp?: string | null;
  userAgent?: string | null;
}
export const customerPromotionApi = {
  create: (customerId: Id) => postRequest<CustomerPromotion>('/scm/customer/promotion/create', {customerId}),
  revoke: (id: Id) => postRequest<string>('/scm/customer/promotion/revoke', {id}),
  list: (customerId: Id) => getRequest<CustomerPromotion[]>('/scm/customer/promotion/list', {customerId}),
  scans: (codeId: Id, limit = 20) => getRequest<CustomerPromotionScan[]>('/scm/customer/promotion/scans', {codeId, limit}),
  /** 最近一次扫码归属，用于新增客户时预填业务员。 */
  attribution: () => getRequest<CustomerPromotionScan | null>('/scm/customer/promotion/attribution', {}),
};
