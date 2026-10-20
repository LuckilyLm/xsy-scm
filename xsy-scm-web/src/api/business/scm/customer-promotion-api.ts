/**
 * 客户专属推广二维码：生成、撤销、扫码记录。
 *
 * 码属于客户，归属业务员取该客户当前的 seller_id 快照；扫码只记录来源，不改任何归属。
 */
import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmResponse} from '/@/types/business/scm/customer';
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
    create: (customerId: Id) =>
        postRequest('/scm/customer/promotion/create', {customerId}) as unknown as Promise<
            ScmResponse<CustomerPromotion>
        >,
    revoke: (id: Id) =>
        postRequest('/scm/customer/promotion/revoke', {id}) as unknown as Promise<ScmResponse<string>>,
    list: (customerId: Id) =>
        getRequest('/scm/customer/promotion/list', {customerId}) as unknown as Promise<
            ScmResponse<CustomerPromotion[]>
        >,
    scans: (codeId: Id, limit = 20) =>
        getRequest('/scm/customer/promotion/scans', {codeId, limit}) as unknown as Promise<
            ScmResponse<CustomerPromotionScan[]>
        >,
    /** 最近一次扫码归属，用于新增客户时预填业务员。 */
    attribution: () =>
        getRequest('/scm/customer/promotion/attribution', {}) as unknown as Promise<
            ScmResponse<CustomerPromotionScan | null>
        >,
};
