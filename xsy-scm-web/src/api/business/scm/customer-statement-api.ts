import {getRequest, postDownload, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ReportId} from '/@/views/business/scm/report/report-types';
export interface CustomerStatementForm {
  settlementCustomerId: ReportId;
  customerId?: ReportId;
  startDate: string;
  endDate: string;
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
