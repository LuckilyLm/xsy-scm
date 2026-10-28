import {getRequest, postDownload, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ReportId} from '/@/views/business/scm/report/report-types';
export interface CustomerStatementForm {
  settlementCustomerId: ReportId;
  customerId?: ReportId;
  startDate: string;
  endDate: string;
  /** 是否把过账记录一并列进对账单；默认不包含。 */
  includePosting?: boolean;
}
export interface CustomerStatementLine {
  statementId: ReportId;
  lineNo: number;
  factType: string;
  factId: ReportId;
  eventAt: string;
  documentNo: string;
  relatedNo?: string;
  customerId: ReportId;
  customerName: string;
  receivableDelta: string;
  receiptDelta: string;
  writeOffDelta: string;
  refundDelta: string;
  /** 只有过账行有值；不是应收/收款变动，不参与滚动余额。 */
  postingAmount?: string | null;
  receivableBalance: string;
}
export interface CustomerStatement {
  id: ReportId;
  settlementCustomerId: ReportId;
  settlementCustomerName: string;
  customerId?: ReportId;
  startDate: string;
  endDate: string;
  partialScope: boolean;
  /** 生成时是否包含过账记录。 */
  includePosting?: boolean;
  generatedAt: string;
  openingReceivable: string;
  receivableIncrease: string;
  receivableRed: string;
  writeOffNet: string;
  /** 本期抹零净额：抹零没有资金流入，单独列示，不并进核销净额。 */
  waiverNet?: string | null;
  closingReceivable: string;
  openingUnallocated: string | null;
  receiptNet: string;
  closingUnallocated: string | null;
  refundNet: string;
  items?: CustomerStatementLine[];
}
const base = '/scm/report/customer/statement';
export const customerStatementApi = {
  freeze: (form: CustomerStatementForm) => postRequest<CustomerStatement>(`${base}/freeze`, form),
  history: (settlementCustomerId: ReportId, options?: RequestOptions) =>
    getRequest<CustomerStatement[]>(`${base}/history`, {settlementCustomerId}, options),
  detail: (id: ReportId, options?: RequestOptions) => getRequest<CustomerStatement>(`${base}/${id}`, {}, options),
  export: (id: ReportId) => postDownload(`${base}/${id}/export`, {}),
};
