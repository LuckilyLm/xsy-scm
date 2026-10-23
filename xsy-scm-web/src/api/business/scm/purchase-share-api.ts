/**
 * 采购单二维码分享：生成一次性展示的分享链接、撤销、查询历史。
 *
 * 令牌明文只在 create 的响应里出现一次，之后列表只回显前缀；前端不缓存令牌。
 */
import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmResponse} from '/@/types/business/scm/customer';
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
  /** 生成分享链接；同一张单上的旧链接当场失效。 */
  create: (purchaseOrderId: Id, validDays?: number) =>
    postRequest('/scm/purchase/share/create', {purchaseOrderId, validDays}) as unknown as Promise<ScmResponse<PurchaseShare>>,
  revoke: (id: Id) => postRequest('/scm/purchase/share/revoke', {id}) as unknown as Promise<ScmResponse<string>>,
  list: (purchaseOrderId: Id) => getRequest('/scm/purchase/share/list', {purchaseOrderId}) as unknown as Promise<ScmResponse<PurchaseShare[]>>,
};
