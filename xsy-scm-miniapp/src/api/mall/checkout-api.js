/*
 * 商城结算与下单接口
 *
 * ⚠️ 后端尚未实现，路径与字段以后端冻结契约为准。
 *
 * 这是全链路契约最敏感的两个接口（规划文档 §17）：
 *
 *   POST /scm/mall/orders/preview  → 返回明细 + priceFingerprint
 *   POST /scm/mall/orders          → 携带同一 priceFingerprint + Idempotency-Key
 *
 * 客户端职责：
 * 1. Idempotency-Key 必带，与「商品 + 数量 + 地址 + 价格指纹」的内容签名绑定；
 *    内容不变时网络重试**复用同一个 key**，内容变化或提交成功后才换新 key；
 * 2. 服务端检测到价格变化会拒绝提交（返回变价语义的错误码），
 *    前端必须重新 preview 并明确提示用户，不得静默重试；
 * 3. 提交按钮需有 in-flight 互斥，防连点。
 *
 * 库存校验、锁余额、建预留、写 sales_order 全部在服务端同一事务内完成，
 * 客户端**不得**先查库存再提交。
 */
import { postRequest } from '@/lib/smart-request';

export const mallCheckoutApi = {
  /**
   * 结算预览
   * @param {{items: Array<{skuId: number, quantity: string}>, addressId: number}} param
   */
  preview: (param) => postRequest('/scm/mall/orders/preview', param),

  /**
   * 提交订单
   * @param {object} param  { items, addressId, priceFingerprint, remark?, deliveryTime? }
   * @param {string} idempotencyKey 由调用方生成并在内容不变时复用
   */
  submit: (param, idempotencyKey) => postRequest('/scm/mall/orders', param, { idempotencyKey }),
};
