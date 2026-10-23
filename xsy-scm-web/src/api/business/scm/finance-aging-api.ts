import {postDownload, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmId, ScmPage} from '/@/types/business/scm/customer';
export type AgingAccountType = 'RECEIVABLE' | 'PAYABLE';
export type AgingBucket = 'UNKNOWN' | 'NOT_DUE' | 'DAYS_1_30' | 'DAYS_31_60' | 'DAYS_61_90' | 'DAYS_91_180' | 'OVER_180';
export interface AgingQuery {
  accountType: AgingAccountType;
  asOfDate: string;
  agingBucket?: AgingBucket;
  customerId?: ScmId;
  settlementCustomerId?: ScmId;
  supplierId?: ScmId;
  warehouseId?: ScmId;
  keyword?: string;
  pageNum: number;
  pageSize: number;
}
export interface AgingRow {
  accountType: AgingAccountType;
  documentId: ScmId;
  documentNo: string;
  sourceId: ScmId;
  sourceNo: string;
  counterpartyId: ScmId;
  counterpartyName: string;
  settlementCustomerId?: ScmId | null;
  settlementCustomerName?: string | null;
  eventAt: string;
  dueDate?: string | null;
  overdueDays?: number | null;
  agingBucket: AgingBucket;
  amount: string;
  redAmount: string;
  netAmount: string;
  writtenOffAmount: string;
  openAmount: string;
}
export interface AgingSummary {
  agingBucket: AgingBucket;
  documentCount: number;
  openAmount: string;
}
const base = '/scm/report/finance/aging';
export const financeAgingApi = {
  query: (data: AgingQuery, options?: RequestOptions) => postRequest<ScmPage<AgingRow>>(`${base}/query`, data, options),
  summary: (data: AgingQuery, options?: RequestOptions) => postRequest<AgingSummary[]>(`${base}/summary`, data, options),
  export: (data: AgingQuery) => postDownload(`${base}/export`, data),
};
