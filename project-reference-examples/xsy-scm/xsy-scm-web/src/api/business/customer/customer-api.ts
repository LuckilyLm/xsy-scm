/*
 * 客户接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const customerApi = {
  query: (param) => postRequest('/customer/query', param),
  queryAll: () => getRequest('/customer/queryAll'),
  add: (param) => postRequest('/customer/add', param),
  update: (param) => postRequest('/customer/update', param),
  delete: (customerId) => getRequest(`/customer/delete/${customerId}`),
  batchDelete: (idList) => postRequest('/customer/batchDelete', idList),
};
