import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmId, ScmResponse} from '/@/types/business/scm/customer';
import {purchaseCommand} from '/@/api/business/scm/purchase-order-api';

export interface InquiryCandidate {
    skuId: ScmId;
    skuCode: string;
    productName: string;
    demandUnit: string;
    requiredQuantity: string;
    existingInquiryId: ScmId | null;
}
export interface PurchaseInquiry {
    id: ScmId;
    batchId: ScmId;
    warehouseId: ScmId;
    purchaserId: ScmId | null;
    skuId: ScmId;
    skuCodeSnapshot: string;
    productNameSnapshot: string;
    demandUnit: string;
    requiredQuantity: string;
    deadlineAt: string;
    status: 'OPEN' | 'ACCEPTED';
    version: number;
    acceptedQuoteId: ScmId | null;
    acceptedAt: string | null;
    acceptedBy: string | null;
    createdAt: string;
    createdBy: string;
}
export interface PurchaseInquiryQuote {
    id: ScmId;
    inquiryId: ScmId;
    supplierId: ScmId;
    supplierNameSnapshot: string;
    revision: number;
    purchaseUnit: string;
    unitPrice: string;
    validUntil: string;
    remark: string | null;
    recordedAt: string;
    recordedBy: string;
}
export interface InquiryBatchPage {
    records: PurchaseInquiry[];
    total: number;
}
export interface InquiryHistoryPage {
    records: PurchaseInquiryQuote[];
    hasMore: boolean;
    nextBeforeId: ScmId | null;
}
export interface PurchaseInquiryDetail {
    inquiry: PurchaseInquiry;
    currentOffers: PurchaseInquiryQuote[];
    quotes: PurchaseInquiryQuote[];
    hasMoreQuotes: boolean;
    nextQuoteBeforeId: ScmId | null;
}

const prefix = '/scm/purchase/inquiry';
export const purchaseInquiryApi = {
    byBatch: (batchId: ScmId, page = 1) => postRequest(prefix + '/batch', {batchId, page}) as unknown as Promise<ScmResponse<InquiryBatchPage>>,
    candidates: (batchId: ScmId) => postRequest(prefix + '/batch/candidates', {batchId}) as unknown as Promise<ScmResponse<InquiryCandidate[]>>,
    detail: (id: ScmId) => getRequest(prefix + '/' + id, {}) as unknown as Promise<ScmResponse<PurchaseInquiryDetail>>,
    history: (inquiryId: ScmId, beforeId: ScmId) => postRequest(prefix + '/history', {inquiryId, beforeId}) as unknown as Promise<ScmResponse<InquiryHistoryPage>>,
    create: (data: {batchId: ScmId; skuId: ScmId; demandUnit: string; deadlineAt: string}) =>
        purchaseCommand<PurchaseInquiryDetail>(prefix + '/create', data),
    quote: (data: {inquiryId: ScmId; supplierId: ScmId; version: number; unitPrice: string; validUntil: string; remark?: string}) =>
        purchaseCommand<PurchaseInquiryDetail>(prefix + '/quote', data),
    accept: (data: {inquiryId: ScmId; quoteId: ScmId; version: number}) =>
        purchaseCommand<PurchaseInquiryDetail>(prefix + '/accept', data),
};
