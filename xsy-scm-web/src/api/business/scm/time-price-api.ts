/** 时价：按时间段生效的临时价格。区间左闭右开，同一规格不允许重叠。 */
import {postRequest} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
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
  query: (data: TimePriceQuery) => postRequest('/scm/pricing/time-price/query', data) as unknown as Promise<ScmResponse<ScmPage<TimePrice>>>,
  add: (data: TimePricePayload) => postRequest('/scm/pricing/time-price/add', data) as unknown as Promise<ScmResponse<TimePrice>>,
  update: (data: TimePricePayload) => postRequest('/scm/pricing/time-price/update', data) as unknown as Promise<ScmResponse<TimePrice>>,
  remove: (id: Id, version: number) => postRequest('/scm/pricing/time-price/delete', {id, version}) as unknown as Promise<ScmResponse<string>>,
};
