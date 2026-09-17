/*
 * 供应商接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const supplierApi = {
  query: (param) => postRequest('/supplier/query', param),
  queryAll: () => getRequest('/supplier/queryAll'),
  add: (param) => postRequest('/supplier/add', param),
  update: (param) => postRequest('/supplier/update', param),
  delete: (supplierId) => getRequest(`/supplier/delete/${supplierId}`),
  batchDelete: (idList) => postRequest('/supplier/batchDelete', idList),
};
