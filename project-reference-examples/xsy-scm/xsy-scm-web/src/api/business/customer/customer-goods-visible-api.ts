/*
 * 客户商品可见接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const customerGoodsVisibleApi = {
  query: (param) => postRequest('/customer/goodsVisible/query', param),
  add: (param) => postRequest('/customer/goodsVisible/add', param),
  update: (param) => postRequest('/customer/goodsVisible/update', param),
  delete: (id) => getRequest(`/customer/goodsVisible/delete/${id}`),
  batchDelete: (idList) => postRequest('/customer/goodsVisible/batchDelete', idList),
};
