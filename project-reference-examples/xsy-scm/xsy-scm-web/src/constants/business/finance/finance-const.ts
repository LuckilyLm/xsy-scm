/*
 * 财务管理枚举
 *
 * 与后端 com.xsy.scm.admin.module.business.finance.constant 下枚举保持一致
 */

import { SmartEnum } from '/@/types/smart-enum';

/**
 * 应收单状态：1 待收款，2 部分收款，3 已结清
 */
export const RECEIVABLE_STATUS_ENUM: SmartEnum<number> = {
  PENDING: { value: 1, desc: '待收款' },
  PARTIAL: { value: 2, desc: '部分收款' },
  SETTLED: { value: 3, desc: '已结清' },
};

/**
 * 收款单状态：1 待确认，2 已确认，3 已驳回
 */
export const PAYMENT_STATUS_ENUM: SmartEnum<number> = {
  PENDING: { value: 1, desc: '待确认' },
  CONFIRMED: { value: 2, desc: '已确认' },
  REJECTED: { value: 3, desc: '已驳回' },
};

/**
 * 收款渠道：1 现金，2 转账，3 在线支付，4 余额扣减
 */
export const PAY_CHANNEL_ENUM: SmartEnum<number> = {
  CASH: { value: 1, desc: '现金' },
  TRANSFER: { value: 2, desc: '转账' },
  ONLINE: { value: 3, desc: '在线支付' },
  BALANCE: { value: 4, desc: '余额扣减' },
};

export default {
  RECEIVABLE_STATUS_ENUM,
  PAYMENT_STATUS_ENUM,
  PAY_CHANNEL_ENUM,
};
