/*
 * 商城订单接口：查询、结算、下单、取消、再来一单
 *
 * 契约要点（规划 §17）：
 * - preview 返回明细 + priceFingerprint；submit 必须回传同一指纹，否则服务端拒绝，前端须重新 preview 并明确提示；
 * - submit / cancel 必带 Idempotency-Key：内容不变时重试复用同一个 key，重复点击不会生成第二张单或第二次取消；
 * - 服务端重验客户、地址、商品和价格后写 sales_order；库存仍按「先接单、后采购」由后台显式预留；
 * - cancel 只能成功于服务端状态机允许的状态，客户端不做「能不能取消」的最终判断，只按 cancelable 置灰；
 * - reorder 由服务端按当前可见性与客户价重新校验，客户端不得回传或复用历史成交价；
 */
import {getRequest, postRequest} from '@/lib/smart-request';

export const mallOrderApi = {
  /** 按当前客户和收货地址列出允许选择的配送仓。 */
  getWarehouses: (addressId) => getRequest('/scm/mall/warehouses', {addressId}),

  /**
   * 当前客户的订单分页
   * @param {{pageNum: number, pageSize: number, displayStatus?: string}} param
   *        displayStatus 取值 PENDING / PREPARING / DELIVERING / COMPLETED / CANCELLED，留空为全部
   */
  getList: (param) => getRequest('/scm/mall/orders', param),

  /** 当前客户的订单详情（含明细、实重、金额与处理记录） */
  getDetail: (orderId) => getRequest(`/scm/mall/orders/${orderId}`),

  /**
   * 结算预览
   * @param {{items: Array<{skuId: number, quantity: string}>, addressId?: number, couponInstanceId?: number}} param
   */
  preview: (param) => postRequest('/scm/mall/orders/preview', param),

  /**
   * 提交订单
   * @param {{items: Array<{skuId: number, quantity: string}>, addressId: number,
   *          priceFingerprint: string, remark?: string, expectDeliveryDate?: string, couponInstanceId?: number}} param
   * @param {string} idempotencyKey 内容不变时复用
   */
  submit: (param, idempotencyKey) => postRequest('/scm/mall/orders', param, {idempotencyKey}),

  /**
   * 取消订单
   * @param {number} orderId
   * @param {string} reason 取消原因，必填
   * @param {string} idempotencyKey 同一订单同一原因重试时复用
   */
  cancel: (orderId, reason, idempotencyKey) => postRequest(`/scm/mall/orders/${orderId}/cancel`, {reason}, {idempotencyKey}),

  /**
   * 再来一单：服务端按当前可见性与客户价重新校验后写入购物车
   * @param {number} orderId
   */
  reorder: (orderId) => postRequest(`/scm/mall/orders/${orderId}/reorder`),

  /**
   * 下单商品汇总：按商品规格聚合当前客户自己的历史下单。
   * @param {{pageNum: number, pageSize: number, startDate?: string, endDate?: string}} param
   *        startDate / endDate 为 yyyy-MM-dd，均含当天；留空表示不限区间
   */
  getSummary: (param) => getRequest('/scm/mall/orders/summary', param),
};
