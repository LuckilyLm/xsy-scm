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
  /** 对账口径：外部对账金额 = 当月应收净额 + 有效过账金额。 */
  statementAmount: string;
  paidAmount: string;
  unpaidAmount: string;
  /** 暂无销售发票台账，首版按对账金额暂记。 */
  invoiceAmount: string;
  postingAmount: string;
  /** 有效过账按登记税点折算的税金。 */
  postingTaxAmount: string;
  /** 实际货款 = 对账金额 − 过账金额 − 过账税金。 */
  actualGoodsAmount: string;
  /** 对账金额 × 1%。 */
  invoiceTaxAmount: string;
  /** 对账金额 × 6%。 */
  serviceFeeAmount: string;
  /** 对账金额 − 成本 − 1% 税 − 6% 服务费 − 运费 − 扣点；成本缺失时为空。 */
  statementProfit: string | null;
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
