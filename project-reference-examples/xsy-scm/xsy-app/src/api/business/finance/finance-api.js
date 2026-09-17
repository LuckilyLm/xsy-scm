/*
 * 财务管理接口（应收单 / 收款单）
 * 与后端 /finance/* 保持一致
 */
import { postRequest } from '@/lib/smart-request';

export const financeApi = {
  queryReceivable: (param) => postRequest('/finance/receivable/query', param),
  queryPayment: (param) => postRequest('/finance/payment/query', param),
  addPayment: (param) => postRequest('/finance/payment', param),
  confirmPayment: (paymentId) => postRequest(`/finance/payment/confirm/${paymentId}`),
};
