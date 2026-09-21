import {getRequest, postRequest} from '/@/lib/axios';
import {orderCommand} from './order-api';
import type {ScmResponse, ScmPage} from '/@/types/business/scm/customer';
import type {ReturnRow, Query, Id} from '/@/views/business/scm/order/order-types';

export const orderReturnApi = {
    query: (data: Query) => postRequest('/scm/order/return/query', data) as unknown as Promise<ScmResponse<ScmPage<ReturnRow>>>,
    detail: (id: Id) => getRequest('/scm/order/return/detail/' + id, {}) as unknown as Promise<ScmResponse<ReturnRow>>,
    create: (data: unknown) => orderCommand<ReturnRow>('/scm/order/return/create', data),
    approve: (data: unknown) => orderCommand<ReturnRow>('/scm/order/return/approve', data),
    reject: (data: unknown) => orderCommand<ReturnRow>('/scm/order/return/reject', data),
    cancel: (data: unknown) => orderCommand<ReturnRow>('/scm/order/return/cancel', data),
};
