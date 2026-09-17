/*
 * 客户账期接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const customerPeriodApi = {
  query: (param) => postRequest('/customer/period/query', param),
  add: (param) => postRequest('/customer/period/add', param),
  update: (param) => postRequest('/customer/period/update', param),
  delete: (periodId) => getRequest(`/customer/period/delete/${periodId}`),
  batchDelete: (idList) => postRequest('/customer/period/batchDelete', idList),
};
