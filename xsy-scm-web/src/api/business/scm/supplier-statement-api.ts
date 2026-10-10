import {getRequest, postDownload, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmResponse} from '/@/types/business/scm/customer';
import type {ReportId} from '/@/views/business/scm/report/report-types';

export interface SupplierStatementForm {
    supplierId: ReportId;
    warehouseId?: ReportId;
    startDate: string;
    endDate: string;
}
export interface SupplierStatementLine {
    statementId: ReportId;
    lineNo: number;
    factType: string;
    factId: ReportId;
    eventAt: string;
    documentNo: string;
    relatedNo?: string;
    payableDelta: string;
    paymentDelta: string;
    writeOffDelta: string;
    payableBalance: string;
}
export interface SupplierStatement {
    id: ReportId;
    supplierId: ReportId;
    supplierName: string;
    warehouseId?: ReportId;
    startDate: string;
    endDate: string;
    partialScope: boolean;
    generatedAt: string;
    openingPayable: string;
    payableIncrease: string;
    payableRed: string;
    writeOffNet: string;
    closingPayable: string;
    openingUnallocated: string | null;
    paymentNet: string;
    closingUnallocated: string | null;
    items?: SupplierStatementLine[];
}
const base = '/scm/report/supplier/statement';
export const supplierStatementApi = {
    freeze: (form: SupplierStatementForm) =>
        postRequest(`${base}/freeze`, form) as unknown as Promise<ScmResponse<SupplierStatement>>,
    history: (supplierId: ReportId, options?: RequestOptions) =>
        getRequest(`${base}/history`, {supplierId}, options) as unknown as Promise<ScmResponse<SupplierStatement[]>>,
    detail: (id: ReportId, options?: RequestOptions) =>
        getRequest(`${base}/${id}`, {}, options) as unknown as Promise<ScmResponse<SupplierStatement>>,
    export: (id: ReportId) => postDownload(`${base}/${id}/export`, {}),
};
