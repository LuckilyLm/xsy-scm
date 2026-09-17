/*
 * 销售退款单接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const orderRefundApi = {
  query: (param) => postRequest('/order/refund/query', param),
  add: (param) => postRequest('/order/refund/add', param),
  update: (param) => postRequest('/order/refund/update', param),
  delete: (refundId) => getRequest(`/order/refund/delete/${refundId}`),
  batchDelete: (idList) => postRequest('/order/refund/batchDelete', idList),
};
