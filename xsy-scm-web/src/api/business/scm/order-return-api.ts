import {getRequest, postRequest} from '/@/lib/axios';
import type {RequestOptions} from '/@/lib/axios';
import {orderCommand} from './order-api';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {ReturnRow, ReturnPricePreview, Query, Id} from '/@/views/business/scm/order/order-types';
export const orderReturnApi = {
  query: (data: Query, options?: RequestOptions) => postRequest<ScmPage<ReturnRow>>('/scm/order/return/query', data, options),
  detail: (id: Id, options?: RequestOptions) => getRequest<ReturnRow>('/scm/order/return/detail/' + id, {}, options),
  create: (data: unknown) => orderCommand<ReturnRow>('/scm/order/return/create', data),
  approve: (data: unknown) => orderCommand<ReturnRow>('/scm/order/return/approve', data),
  /** 独立退货：不关联原订单，明细按 SKU 建行。 */
  createIndependent: (data: unknown) => orderCommand<ReturnRow>('/scm/order/return/independent/create', data),
  approveIndependent: (data: unknown) => orderCommand<ReturnRow>('/scm/order/return/independent/approve', data),
  /** 建单前取价预览：最近成交价优先，其次定价链。 */
  previewIndependentPrices: (data: {customerId: Id; skuIds: Id[]}) =>
    postRequest<ReturnPricePreview[]>('/scm/order/return/independent/price/preview', data),
  receive: (data: unknown) => orderCommand('/scm/order/return/receive', data),
  reject: (data: unknown) => orderCommand<ReturnRow>('/scm/order/return/reject', data),
  cancel: (data: unknown) => orderCommand<ReturnRow>('/scm/order/return/cancel', data),
};
