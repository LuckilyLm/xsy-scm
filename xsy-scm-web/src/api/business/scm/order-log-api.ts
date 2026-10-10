import {postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmResponse, ScmPage} from '/@/types/business/scm/customer';
import type {LogRow, Query} from '/@/views/business/scm/order/order-types';

export const orderLogApi = {
    query: (data: Query, options?: RequestOptions) =>
        postRequest('/scm/order/log/query', data, options) as unknown as Promise<ScmResponse<ScmPage<LogRow>>>,
};
