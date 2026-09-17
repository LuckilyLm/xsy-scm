/*
 * 财务管理接口（应收单 / 收款单）
 *
 * 与后端 com.xsy.scm.admin.module.business.finance.controller 保持一致
 */
import { postRequest } from '/@/lib/axios';

export const financeApi = {
  // 应收单分页查询
  queryReceivable: (param) => postRequest('/finance/receivable/query', param),
  // 收款单分页查询
  queryPayment: (param) => postRequest('/finance/payment/query', param),
  // 登记收款单（客户回款）：生成待确认收款单，确认后核销应收
  addPayment: (param) => postRequest('/finance/payment', param),
  // 确认收款并核销应收：收款单转「已确认」，应收冲减待收余额
  confirmPayment: (paymentId) => postRequest(`/finance/payment/confirm/${paymentId}`),
};
