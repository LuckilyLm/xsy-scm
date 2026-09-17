/*
 * 产品价格接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const productPriceApi = {
  query: (param) => postRequest('/product/price/query', param),
  add: (param) => postRequest('/product/price/add', param),
  update: (param) => postRequest('/product/price/update', param),
  delete: (priceId) => getRequest(`/product/price/delete/${priceId}`),
  batchDelete: (idList) => postRequest('/product/price/batchDelete', idList),
};
