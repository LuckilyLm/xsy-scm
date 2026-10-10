import {getRequest, postDownload, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmResponse} from '/@/types/business/scm/customer';
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
    closingReceivable: string;
    openingUnallocated: string | null;
    receiptNet: string;
    closingUnallocated: string | null;
    refundNet: string;
    items?: CustomerStatementLine[];
}
const base = '/scm/report/customer/statement';
export const customerStatementApi = {
    freeze: (form: CustomerStatementForm) =>
        postRequest(`${base}/freeze`, form) as unknown as Promise<ScmResponse<CustomerStatement>>,
    history: (settlementCustomerId: ReportId, options?: RequestOptions) =>
        getRequest(`${base}/history`, {settlementCustomerId}, options) as unknown as Promise<ScmResponse<CustomerStatement[]>>,
    detail: (id: ReportId, options?: RequestOptions) =>
        getRequest(`${base}/${id}`, {}, options) as unknown as Promise<ScmResponse<CustomerStatement>>,
    export: (id: ReportId) => postDownload(`${base}/${id}/export`, {}),
};
