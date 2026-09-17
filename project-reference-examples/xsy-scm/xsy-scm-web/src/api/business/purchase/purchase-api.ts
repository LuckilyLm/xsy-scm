/*
 * 采购订单接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const purchaseApi = {
  query: (param) => postRequest('/purchase/query', param),
  add: (param) => postRequest('/purchase/add', param),
  update: (param) => postRequest('/purchase/update', param),
  delete: (purchaseId) => getRequest(`/purchase/delete/${purchaseId}`),
  batchDelete: (idList) => postRequest('/purchase/batchDelete', idList),
  accept: (purchaseId) => postRequest(`/purchase/accept/${purchaseId}`),
};
