import {getRequest, postRequest} from '/@/lib/axios';
import {orderCommand} from './order-api';
import type {ScmResponse, ScmPage} from '/@/types/business/scm/customer';
import type {RefundRow, Query, Id} from '/@/views/business/scm/order/order-types';

export const orderRefundApi = {
    query: (data: Query) => postRequest('/scm/order/refund/query', data) as unknown as Promise<ScmResponse<ScmPage<RefundRow>>>,
    detail: (id: Id) => getRequest('/scm/order/refund/detail/' + id, {}) as unknown as Promise<ScmResponse<RefundRow>>,
    returnBalance: (refundId: Id) => orderCommand('/scm/balance/refund', {refundId}),
    complete: (data: unknown) => orderCommand<RefundRow>('/scm/order/refund/complete', data),
};
