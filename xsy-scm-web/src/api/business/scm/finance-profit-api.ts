import {postDownload, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {ReportDateQuery, ReportId, ReportPage} from '/@/views/business/scm/report/report-types';

export type FinanceProfitDimension = 'DAY' | 'CUSTOMER' | 'PRODUCT' | 'CATEGORY' | 'SELLER' | 'WAREHOUSE';

export interface FinanceProfitQuery extends ReportPage, ReportDateQuery {
    dimension: FinanceProfitDimension;
    customerId?: ReportId;
    sellerId?: number;
    skuId?: ReportId;
    categoryId?: ReportId;
    warehouseId?: ReportId;
    keyword?: string;
}

export interface FinanceProfitRow {
    dimensionId?: ReportId | null;
    dimensionName: string;
    dimensionCode?: string | null;
    bizDate?: string | null;
    revenueAmount: string | null;
    salesCostAmount: string | null;
    /** 促销赠品成本（满赠赠品出库成本）；与商品销售成本分开，见后端 `ScmFinanceProfitRowVO`。 */
    giftCostAmount?: string | null;
    grossProfit: string | null;
    grossMarginRate: string | null;
    costMissingCount: number | null;
}

export interface FinanceProfitSummary {
    revenueAmount: string | null;
    salesCostAmount: string | null;
    /** 促销赠品成本（满赠赠品出库成本）。 */
    giftCostAmount?: string | null;
    grossProfit: string | null;
    grossMarginRate: string | null;
    costMissingCount: number | null;
}

const base = '/scm/report/finance/profit';
export const financeProfitApi = {
    query: (data: FinanceProfitQuery, options?: RequestOptions) =>
        postRequest(`${base}/query`, data, options) as unknown as Promise<ScmResponse<ScmPage<FinanceProfitRow>>>,
    summary: (data: Partial<FinanceProfitQuery>, options?: RequestOptions) =>
        postRequest(`${base}/summary`, data, options) as unknown as Promise<ScmResponse<FinanceProfitSummary>>,
    export: (data: Partial<FinanceProfitQuery>) => postDownload(`${base}/export`, data),
};
