/*
 * 商城购物车接口
 *
 * 购物车为**服务端**购物车（跨端一致、换机不丢）。
 *
 * 约束（规划 §16）：
 * - 数量一律以字符串定点传递，禁止用 Number() 求和；
 * - 失效 / 不可售 / 无价格的商品**保留在列表中**，由服务端 availability 标记原因；
 * - 所有写操作返回刷新后的整车，前端不做本地乐观合并。
 */
import { deleteRequest, getRequest, postRequest, putRequest } from '@/lib/smart-request';

export const mallCartApi = {
  /** 购物车列表（含预估金额与可结算行数） */
  getCart: () => getRequest('/scm/mall/cart'),

  /**
   * 加入购物车（同商品规格累加数量）
   * @param {{skuId: number, quantity: string}} param quantity 必须是字符串
   */
  addItem: (param) => postRequest('/scm/mall/cart/items', param),

  /**
   * 修改数量（绝对值）
   * @param {number} skuId
   * @param {string} quantity
   */
  updateItem: (skuId, quantity) => putRequest(`/scm/mall/cart/items/${skuId}`, { quantity }),

  /** 移除单项 */
  removeItem: (skuId) => deleteRequest(`/scm/mall/cart/items/${skuId}`),

  /**
   * 批量移除
   * @param {number[]} skuIds
   */
  removeItems: (skuIds) => postRequest('/scm/mall/cart/items/remove', { skuIds }),

  /** 清空购物车 */
  clear: () => deleteRequest('/scm/mall/cart'),
};
