/*
 * 销售订单明细接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const orderItemApi = {
  query: (param) => postRequest('/order/item/query', param),
  add: (param) => postRequest('/order/item/add', param),
  update: (param) => postRequest('/order/item/update', param),
  delete: (itemId) => getRequest(`/order/item/delete/${itemId}`),
  batchDelete: (idList) => postRequest('/order/item/batchDelete', idList),
};
