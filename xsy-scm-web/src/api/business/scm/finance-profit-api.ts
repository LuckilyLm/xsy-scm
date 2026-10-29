import {postDownload, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
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
  /** 促销赠品出库成本，与商品销售成本分开。 */
  giftCostAmount?: string | null;
  grossProfit: string | null;
  grossMarginRate: string | null;
  costMissingCount: number | null;
}
export interface FinanceProfitSummary {
  revenueAmount: string | null;
  salesCostAmount: string | null;
  /** 促销赠品出库成本。 */
  giftCostAmount?: string | null;
  grossProfit: string | null;
  grossMarginRate: string | null;
  costMissingCount: number | null;
}
const base = '/scm/report/finance/profit';
export interface CustomerMonthlyProfitRow extends FinanceProfitRow {
  taxAmount: string;
  serviceAmount: string;
  freightAmount: string;
  commissionAmount: string;
  expenseAmount: string;
  netProfit: string | null;
}
export const financeProfitApi = {
  monthlyQuery: (data: FinanceProfitQuery, options?: RequestOptions) =>
    postRequest<ScmPage<CustomerMonthlyProfitRow>>(`${base}/monthly/query`, data, options),
  monthlySummary: (data: FinanceProfitQuery, options?: RequestOptions) =>
    postRequest<CustomerMonthlyProfitRow>(`${base}/monthly/summary`, data, options),
  monthlyExport: (data: FinanceProfitQuery & {exportColumns: string[]}) => postDownload(`${base}/monthly/export`, data),
  query: (data: FinanceProfitQuery, options?: RequestOptions) => postRequest<ScmPage<FinanceProfitRow>>(`${base}/query`, data, options),
  summary: (data: Partial<FinanceProfitQuery>, options?: RequestOptions) => postRequest<FinanceProfitSummary>(`${base}/summary`, data, options),
  export: (data: Partial<FinanceProfitQuery>) => postDownload(`${base}/export`, data),
};
