/*
 * 产品规格接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const productSkuApi = {
  query: (param) => postRequest('/product/sku/query', param),
  queryAll: () => getRequest('/product/sku/queryAll'),
  add: (param) => postRequest('/product/sku/add', param),
  update: (param) => postRequest('/product/sku/update', param),
  delete: (skuId) => getRequest(`/product/sku/delete/${skuId}`),
  batchDelete: (idList) => postRequest('/product/sku/batchDelete', idList),
};
