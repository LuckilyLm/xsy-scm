import {getRequest, postDownload, postRequest, type RequestOptions} from '/@/lib/axios';
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
  freeze: (form: SupplierStatementForm) => postRequest<SupplierStatement>(`${base}/freeze`, form),
  history: (supplierId: ReportId, options?: RequestOptions) => getRequest<SupplierStatement[]>(`${base}/history`, {supplierId}, options),
  detail: (id: ReportId, options?: RequestOptions) => getRequest<SupplierStatement>(`${base}/${id}`, {}, options),
  export: (id: ReportId) => postDownload(`${base}/${id}/export`, {}),
};
