import {postDownload, postRequest} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';

export interface PurchaseDailyQuery {
    reportDate: string;
    warehouseId?: number | string;
    keyword?: string;
    pageNum: number;
    pageSize: number;
}

export interface PurchaseDailyProduct {
    skuId: number | string;
    spuCode: string;
    productName: string;
    skuCode: string;
    skuName: string;
    purchaseUnit: string;
    orderCount: number;
    plannedQuantity: string;
    orderAmount: string;
}

export interface PurchaseDailyReport {
    reportDate: string;
    generatedAt: string | null;
    products: ScmPage<PurchaseDailyProduct>;
}

const BASE = '/scm/report/purchase/daily';
export const purchaseDailyReportApi = {
    query: (data: PurchaseDailyQuery) =>
        postRequest(`${BASE}/query`, data) as unknown as Promise<ScmResponse<PurchaseDailyReport>>,
    export: (data: PurchaseDailyQuery) => postDownload(`${BASE}/export`, data),
};
