/*
 * 盘点明细接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const stockCheckItemApi = {
  query: (param) => postRequest('/stock/check/item/query', param),
  add: (param) => postRequest('/stock/check/item/add', param),
  update: (param) => postRequest('/stock/check/item/update', param),
  delete: (itemId) => getRequest(`/stock/check/item/delete/${itemId}`),
  batchDelete: (idList) => postRequest('/stock/check/item/batchDelete', idList),
};
