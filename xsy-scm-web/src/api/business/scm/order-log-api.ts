import {postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {LogRow, Query} from '/@/views/business/scm/order/order-types';
export const orderLogApi = {
  query: (data: Query, options?: RequestOptions) => postRequest<ScmPage<LogRow>>('/scm/order/log/query', data, options),
};
