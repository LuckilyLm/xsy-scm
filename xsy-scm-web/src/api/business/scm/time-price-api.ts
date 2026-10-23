/** 时价：按时间段生效的临时价格。区间左闭右开，同一规格不允许重叠。 */
import {postRequest} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {Id} from '/@/views/business/scm/inventory/inventory-types';
export interface TimePrice {
  id: Id;
  skuId: Id;
  skuCode?: string | null;
  productName?: string | null;
  skuName?: string | null;
  saleUnit?: string | null;
  unitPrice: string;
  startAt: string;
  endAt: string;
  remark?: string | null;
  version: number;
  createdBy?: string | null;
  createdAt?: string | null;
  effectiveNow?: boolean | null;
}
export interface TimePriceQuery {
  pageNum: number;
  pageSize: number;
  keyword?: string;
}
export interface TimePricePayload {
  id?: Id;
  version?: number;
  skuId: Id;
  unitPrice: string;
  startAt: string;
  endAt: string;
  remark?: string | null;
}
export const timePriceApi = {
  query: (data: TimePriceQuery) => postRequest<ScmPage<TimePrice>>('/scm/pricing/time-price/query', data),
  add: (data: TimePricePayload) => postRequest<TimePrice>('/scm/pricing/time-price/add', data),
  update: (data: TimePricePayload) => postRequest<TimePrice>('/scm/pricing/time-price/update', data),
  remove: (id: Id, version: number) => postRequest<string>('/scm/pricing/time-price/delete', {id, version}),
};
