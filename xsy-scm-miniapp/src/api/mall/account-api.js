/*
 * 商城「我的」接口（客户资料 / 账期 / 余额 / 对账 / 消息）
 *
 * ⚠️ 后端尚未实现，路径与字段以后端冻结契约为准。
 *
 * 约束（规划文档 §20 / §32）：
 * - 账期与余额为**只读展示**，数据来自财务域，客户端不做任何计算；
 * - 所有查询按登录客户隔离，服务端强制过滤。
 */
import { getRequest } from '@/lib/smart-request';

export const mallAccountApi = {
  /** 客户资料（门店、业务员、客户类型） */
  getProfile: () => getRequest('/scm/mall/account/profile'),

  /** 账期信息（授信额度、账期类型、结算日）—— 只读 */
  getCredit: () => getRequest('/scm/mall/account/credit'),

  /** 余额 —— 只读 */
  getBalance: () => getRequest('/scm/mall/account/balance'),

  /** 对账单分页 */
  getStatementPage: (param) => getRequest('/scm/mall/account/statements', param),

  /** 下单统计 */
  getOrderStatistics: () => getRequest('/scm/mall/account/order-statistics'),

  /** 消息通知分页 */
  getMessagePage: (param) => getRequest('/scm/mall/account/messages', param),

  /** 未读消息数 */
  getUnreadCount: () => getRequest('/scm/mall/account/messages/unread-count'),

  /** 商城内容页（服务条款 / 售后规则 / 关于我们） */
  getContent: (code) => getRequest(`/scm/mall/account/content/${code}`),
};
