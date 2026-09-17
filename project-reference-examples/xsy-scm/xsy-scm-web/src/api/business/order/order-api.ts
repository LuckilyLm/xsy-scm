/*
 * 销售订单接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const orderApi = {
  query: (param) => postRequest('/order/query', param),
  add: (param) => postRequest('/order/add', param),
  update: (param) => postRequest('/order/update', param),
  delete: (orderId) => getRequest(`/order/delete/${orderId}`),
  batchDelete: (idList) => postRequest('/order/batchDelete', idList),
  // 发货：触发销售出库（库存反向流水），订单置「配送中」并写发货日志
  deliver: (orderId) => postRequest(`/order/deliver/${orderId}`),
  // 确认订单：草稿/待确认 → 已确认，发货的前置动作
  confirm: (orderId) => postRequest(`/order/confirm/${orderId}`),
  // 签收：配送中 → 已签收，并按核算金额生成应收（09-01）
  sign: (orderId) => postRequest(`/order/sign/${orderId}`),
};
