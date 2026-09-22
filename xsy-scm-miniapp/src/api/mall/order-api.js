/*
 * 商城订单接口
 *
 * ⚠️ 后端尚未实现，路径与字段以后端冻结契约为准。
 *
 * 约束（规划文档 §18 / §19）：
 * - 客户侧状态是**服务端内部状态的映射**，客户端不自建状态机、不本地推导流转；
 * - 订单详情必须能解释「金额为何变化」：下单量 → 实际称重 → 结算量 → 最终金额；
 * - 再来一单必须重新取当前价格 / 可见性 / 库存，禁止复制历史订单金额。
 */
import { getRequest, postRequest } from '@/lib/smart-request';

export const mallOrderApi = {
  /**
   * 订单分页
   * @param {{pageNum: number, pageSize: number, status?: string, keyword?: string}} param
   *        status 为**客户侧状态**（如 PENDING_CONFIRM），不是内部状态码
   */
  getOrderPage: (param) => getRequest('/scm/mall/orders', param),

  /**
   * 订单详情
   * @param {number} orderId
   */
  getOrderDetail: (orderId) => getRequest(`/scm/mall/orders/${orderId}`),

  /**
   * 再来一单：服务端按历史订单重新解析当前可见性 / 价格 / 库存，
   * 返回可加购项与失效项，客户端据此重建购物车。
   * @param {number} orderId
   */
  reorder: (orderId) => postRequest(`/scm/mall/orders/${orderId}/reorder`),

  /** 取消订单（仅未确认状态可取消，具体以后端状态机为准） */
  cancel: (orderId) => postRequest(`/scm/mall/orders/${orderId}/cancel`),
};
