import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import {orderCommand} from './order-api';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {RefundRow, Query, Id} from '/@/views/business/scm/order/order-types';
export const orderRefundApi = {
  query: (data: Query, options?: RequestOptions) => postRequest<ScmPage<RefundRow>>('/scm/order/refund/query', data, options),
  detail: (id: Id, options?: RequestOptions) => getRequest<RefundRow>('/scm/order/refund/detail/' + id, {}, options),
  returnBalance: (refundId: Id) => orderCommand('/scm/balance/refund', {refundId}),
  complete: (data: unknown) => orderCommand<RefundRow>('/scm/order/refund/complete', data),
};
