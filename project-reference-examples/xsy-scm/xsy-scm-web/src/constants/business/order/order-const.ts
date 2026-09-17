/*
 * 订单
 *
 * 与后端 com.xsy.scm.admin.module.business.order.constant 下枚举保持一致
 */

import { SmartEnum } from '/@/types/smart-enum';

/**
 * 订单来源：1 商城下单，2 后台录单，3 补单
 */
export const ORDER_SOURCE_ENUM: SmartEnum<number> = {
  MALL: { value: 1, desc: '商城下单' },
  ADMIN: { value: 2, desc: '后台录单' },
  SUPPLEMENT: { value: 3, desc: '补单' },
};

/**
 * 结算方式：1 账期支付，2 货到付款，3 在线支付，4 余额充值
 */
export const SETTLE_TYPE_ENUM: SmartEnum<number> = {
  PERIOD: { value: 1, desc: '账期支付' },
  CASH_ON_DELIVERY: { value: 2, desc: '货到付款' },
  ONLINE: { value: 3, desc: '在线支付' },
  BALANCE: { value: 4, desc: '余额充值' },
};

/**
 * 支付状态：1 未付，2 部分支付，3 已付
 */
export const PAY_STATUS_ENUM: SmartEnum<number> = {
  UNPAID: { value: 1, desc: '未付' },
  PARTIAL: { value: 2, desc: '部分支付' },
  PAID: { value: 3, desc: '已付' },
};

/**
 * 订单状态：1 草稿 ... 12 已作废
 */
export const ORDER_STATUS_ENUM: SmartEnum<number> = {
  DRAFT: { value: 1, desc: '草稿' },
  WAIT_CONFIRM: { value: 2, desc: '待确认' },
  CONFIRMED: { value: 3, desc: '已确认' },
  PURCHASING: { value: 4, desc: '采购中' },
  WAIT_SORTING: { value: 5, desc: '待分拣' },
  SORTING: { value: 6, desc: '分拣中' },
  DELIVERING: { value: 7, desc: '配送中' },
  SIGNED: { value: 8, desc: '已签收' },
  COMPLETED: { value: 9, desc: '已完成' },
  REFUNDING: { value: 10, desc: '退款中' },
  CANCELLED: { value: 11, desc: '已取消' },
  INVALID: { value: 12, desc: '已作废' },
};

/**
 * 价格类型：1 基础价，2 客户分级价，3 时价，4 协议价
 */
export const PRICE_TYPE_ENUM: SmartEnum<number> = {
  BASE: { value: 1, desc: '基础价' },
  CUSTOMER_LEVEL: { value: 2, desc: '客户分级价' },
  CURRENT: { value: 3, desc: '时价' },
  AGREEMENT: { value: 4, desc: '协议价' },
};

/**
 * 订单明细状态：1 正常，2 已退款，3 已退货
 */
export const ORDER_ITEM_STATUS_ENUM: SmartEnum<number> = {
  NORMAL: { value: 1, desc: '正常' },
  REFUNDED: { value: 2, desc: '已退款' },
  RETURNED: { value: 3, desc: '已退货' },
};

/**
 * 订单操作类型：1 创建 ... 10 作废
 */
export const ORDER_OPERATE_TYPE_ENUM: SmartEnum<number> = {
  CREATE: { value: 1, desc: '创建' },
  CONFIRM: { value: 2, desc: '确认' },
  CHANGE_PRICE: { value: 3, desc: '改价' },
  EDIT: { value: 4, desc: '编辑' },
  CANCEL: { value: 5, desc: '取消' },
  DELIVER: { value: 6, desc: '发货' },
  SIGN: { value: 7, desc: '签收' },
  SETTLE: { value: 8, desc: '核算' },
  REFUND: { value: 9, desc: '退款' },
  INVALID: { value: 10, desc: '作废' },
};

/**
 * 退款类型：1 仅退款，2 退货退款
 */
export const REFUND_TYPE_ENUM: SmartEnum<number> = {
  ONLY_REFUND: { value: 1, desc: '仅退款' },
  RETURN_REFUND: { value: 2, desc: '退货退款' },
};

/**
 * 退款状态：1 待审核，2 已通过，3 已退款，4 已驳回
 */
export const REFUND_STATUS_ENUM: SmartEnum<number> = {
  WAIT_AUDIT: { value: 1, desc: '待审核' },
  APPROVED: { value: 2, desc: '已通过' },
  REFUNDED: { value: 3, desc: '已退款' },
  REJECTED: { value: 4, desc: '已驳回' },
};

export default {
  ORDER_SOURCE_ENUM,
  SETTLE_TYPE_ENUM,
  PAY_STATUS_ENUM,
  ORDER_STATUS_ENUM,
  PRICE_TYPE_ENUM,
  ORDER_ITEM_STATUS_ENUM,
  ORDER_OPERATE_TYPE_ENUM,
  REFUND_TYPE_ENUM,
  REFUND_STATUS_ENUM,
};
