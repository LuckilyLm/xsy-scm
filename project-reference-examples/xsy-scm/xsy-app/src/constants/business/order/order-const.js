/*
 * 订单枚举（与后端 com.xsy.scm.admin.module.business.order.constant 保持一致）
 */

// 订单状态：1 草稿 ... 12 已作废（7 配送中 = 发货后状态，C）
const ORDER_STATUS_ENUM = {
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

// 价格类型（B 价格快照）：1 基础价，2 客户分级价，3 时价，4 协议价
const PRICE_TYPE_ENUM = {
  BASE: { value: 1, desc: '基础价' },
  CUSTOMER_LEVEL: { value: 2, desc: '客户分级价' },
  CURRENT: { value: 3, desc: '时价' },
  AGREEMENT: { value: 4, desc: '协议价' },
};

// 支付状态：1 未付，2 部分支付，3 已付
const PAY_STATUS_ENUM = {
  UNPAID: { value: 1, desc: '未付' },
  PARTIAL: { value: 2, desc: '部分支付' },
  PAID: { value: 3, desc: '已付' },
};

// 结算方式：1 账期支付，2 货到付款，3 在线支付，4 余额充值
const SETTLE_TYPE_ENUM = {
  PERIOD: { value: 1, desc: '账期支付' },
  CASH_ON_DELIVERY: { value: 2, desc: '货到付款' },
  ONLINE: { value: 3, desc: '在线支付' },
  BALANCE: { value: 4, desc: '余额充值' },
};

export default {
  ORDER_STATUS_ENUM,
  PRICE_TYPE_ENUM,
  PAY_STATUS_ENUM,
  SETTLE_TYPE_ENUM,
};
