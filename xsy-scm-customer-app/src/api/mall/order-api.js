/*
 * 商城结算与下单接口
 *
 * 契约要点（规划 §17）：
 * - preview 返回明细 + priceFingerprint；submit 必须回传同一指纹，否则服务端拒绝，前端须重新 preview 并明确提示；
 * - submit 必带 Idempotency-Key：内容（商品 + 数量 + 地址 + 备注 + 配送日）不变时重试复用同一个 key；
 * - 服务端重验客户、地址、商品和价格后写 sales_order；库存仍按“先接单、后采购”由后台显式预留。
 */
import { getRequest, postRequest } from '@/lib/smart-request';

export const mallOrderApi = {
  /** 当前客户的订单分页 */
  getList: (param) => getRequest('/scm/mall/orders', param),

  /** 当前客户的订单详情 */
  getDetail: (orderId) => getRequest(`/scm/mall/orders/${orderId}`),

  /**
   * 结算预览
   * @param {{items: Array<{skuId: number, quantity: string}>, addressId?: number}} param
   */
  preview: (param) => postRequest('/scm/mall/orders/preview', param),

  /**
   * 提交订单
   * @param {{items: Array<{skuId: number, quantity: string}>, addressId: number,
   *          priceFingerprint: string, remark?: string, expectDeliveryDate?: string}} param
   * @param {string} idempotencyKey 内容不变时复用
   */
  submit: (param, idempotencyKey) =>
    postRequest('/scm/mall/orders', param, { idempotencyKey }),
};
