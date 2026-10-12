import {postDownload, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {ReportId} from '/@/views/business/scm/report/report-types';

export type OrderExceptionType = 'SORTING_DIFFERENCE' | 'DELIVERY_EXCEPTION' | 'RETURN_REJECTED';
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
    query: (data: OrderExceptionQuery, options?: RequestOptions) =>
        postRequest(`${base}/query`, data, options) as unknown as Promise<ScmResponse<ScmPage<OrderExceptionRow>>>,
    summary: (data: OrderExceptionQuery, options?: RequestOptions) =>
        postRequest(`${base}/summary`, data, options) as unknown as Promise<ScmResponse<OrderExceptionSummary[]>>,
    export: (data: OrderExceptionQuery) => postDownload(`${base}/export`, data),
};
