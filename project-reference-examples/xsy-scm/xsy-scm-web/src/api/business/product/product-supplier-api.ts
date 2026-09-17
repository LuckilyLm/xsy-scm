/*
 * 产品供应商接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const productSupplierApi = {
  query: (param) => postRequest('/product/supplier/query', param),
  add: (param) => postRequest('/product/supplier/add', param),
  update: (param) => postRequest('/product/supplier/update', param),
  delete: (id) => getRequest(`/product/supplier/delete/${id}`),
  batchDelete: (idList) => postRequest('/product/supplier/batchDelete', idList),
};
