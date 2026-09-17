/*
 * 产品接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const productApi = {
  query: (param) => postRequest('/product/query', param),
  queryAll: () => getRequest('/product/queryAll'),
  add: (param) => postRequest('/product/add', param),
  update: (param) => postRequest('/product/update', param),
  delete: (productId) => getRequest(`/product/delete/${productId}`),
  batchDelete: (idList) => postRequest('/product/batchDelete', idList),
};
