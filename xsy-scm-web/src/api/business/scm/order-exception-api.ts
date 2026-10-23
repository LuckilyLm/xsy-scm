import {postDownload, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {ReportId} from '/@/views/business/scm/report/report-types';
export type OrderExceptionType =
  | 'SORTING_SHORTAGE'
  | 'SORTING_DIFFERENCE'
  | 'ORDER_QUANTITY_DIFFERENCE'
  | 'DELIVERY_EXCEPTION'
  | 'RETURN_PENDING'
  | 'REFUND_PENDING'
  | 'RETURN_REJECTED';
export interface OrderExceptionQuery {
  startDate: string;
  endDate: string;
  exceptionType?: OrderExceptionType;
  warehouseId?: ReportId;
  keyword?: string;
  pageNum: number;
  pageSize: number;
}
export interface OrderExceptionRow {
  exceptionType: OrderExceptionType;
  sourceId: ReportId;
  sourceRowId: ReportId;
  sourceNo: string;
  orderId: ReportId;
  orderNo: string;
  customerName: string;
  warehouseId?: ReportId | null;
  warehouseName?: string | null;
  productName?: string | null;
  unit?: string | null;
  stockUnit?: string | null;
  stockQuantity?: string | null;
  stockReservedQuantity?: string | null;
  stockAvailableQuantity?: string | null;
  plannedQuantity?: string | null;
  actualQuantity?: string | null;
  differenceQuantity?: string | null;
  sourceStatus: string;
  reason?: string | null;
  occurredAt: string;
}
export interface OrderExceptionSummary {
  exceptionType: OrderExceptionType;
  exceptionCount: number;
  orderCount: number;
}
const base = '/scm/report/order-exceptions';
export const orderExceptionApi = {
  query: (data: OrderExceptionQuery, options?: RequestOptions) => postRequest<ScmPage<OrderExceptionRow>>(`${base}/query`, data, options),
  summary: (data: OrderExceptionQuery, options?: RequestOptions) => postRequest<OrderExceptionSummary[]>(`${base}/summary`, data, options),
  export: (data: OrderExceptionQuery) => postDownload(`${base}/export`, data),
};
