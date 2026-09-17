/*
 * 采购明细接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const purchaseItemApi = {
  query: (param) => postRequest('/purchase/item/query', param),
  add: (param) => postRequest('/purchase/item/add', param),
  update: (param) => postRequest('/purchase/item/update', param),
  delete: (itemId) => getRequest(`/purchase/item/delete/${itemId}`),
  batchDelete: (idList) => postRequest('/purchase/item/batchDelete', idList),
};
