/*
 * 商城售后接口：申请、撤销、列表、详情
 *
 * 契约要点：
 * - 售后事实只来自正式退货单 / 退款单 / 资金分摊，客户端不推导进度与状态；
 * - apply / cancel 携带 Idempotency-Key：内容不变时重试复用同一个键，重复点击不会重复建单或重复撤销；
 * - 能不能申请（订单状态、可退数量）由服务端权威判定，客户端只按 cancelable 与服务端报错呈现；
 * - 金额一律为服务端下发的四位小数字符串；申请页预估金额仅作展示，以审核为准。
 */
import { getRequest, postRequest } from '@/lib/smart-request';

export const mallAfterSaleApi = {
  /**
   * 当前客户的售后单分页
   * @param {{pageNum: number, pageSize: number, orderId?: number}} param
   *        orderId 只看某一订单的售后，留空为全部
   */
  getList: (param) => getRequest('/scm/mall/after-sales', param),

  /** 售后单详情（含明细、退款结果、原路退回明细与进度节点） */
  getDetail: (returnId) => getRequest(`/scm/mall/after-sales/${returnId}`),

  /**
   * 申请售后
   * @param {{orderId: number, reason: string,
   *          items: Array<{orderItemId: number, quantity: string}>}} param
   * @param {string} idempotencyKey 内容不变时复用
   */
  apply: (param, idempotencyKey) => postRequest('/scm/mall/after-sales', param, { idempotencyKey }),

  /**
   * 撤销待审核的售后申请
   * @param {number} returnId
   * @param {string} idempotencyKey 同一售后单重试时复用
   */
  cancel: (returnId, idempotencyKey) => postRequest(`/scm/mall/after-sales/${returnId}/cancel`, {}, { idempotencyKey }),
};
