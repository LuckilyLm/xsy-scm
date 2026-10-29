/** 权限码与后端枚举、受保护端点权限一致。 */
import type {SmartEnum} from '/@/types/smart-enum';

export const SCM_FINANCE_PERMISSION = {
  RECEIVABLE_QUERY: 'scm:finance:receivable:query',
  PAYABLE_QUERY: 'scm:finance:payable:query',
  RECEIPT_QUERY: 'scm:finance:receipt:query',
  PAYMENT_QUERY: 'scm:finance:payment:query',
  WRITE_OFF_QUERY: 'scm:finance:write-off:query',
  RECEIPT_ADD: 'scm:finance:receipt:add',
  PAYMENT_ADD: 'scm:finance:payment:add',
  WRITE_OFF_ADD: 'scm:finance:write-off:add',
  PAYABLE_RED: 'scm:finance:payable:red',
  WRITE_OFF_REVERSE: 'scm:finance:write-off:reverse',
  /** 抹零与反向抹零分开授权：会抹零不等于能撤销。 */
  WAIVER_ADD: 'scm:finance:waiver:add',
  WAIVER_REVERSE: 'scm:finance:waiver:reverse',
  /** 过账特殊单据：查询、登记、修改、作废各自授权，作废不复用修改。 */
  POSTING_QUERY: 'scm:finance:posting:query',
  POSTING_ADD: 'scm:finance:posting:add',
  POSTING_UPDATE: 'scm:finance:posting:update',
  POSTING_VOID: 'scm:finance:posting:void',
  RECEIPT_REVERSE: 'scm:finance:receipt:reverse',
  PAYMENT_REVERSE: 'scm:finance:payment:reverse',
  EXPORT: 'scm:finance:export',
} as const;

export const SCM_FINANCE_ENTRY_TYPE_ENUM: SmartEnum<string> = {
  NORMAL: {value: 'NORMAL', desc: '正常'},
  RED: {value: 'RED', desc: '红字'},
  REVERSE: {value: 'REVERSE', desc: '反向'},
};

export const SCM_FINANCE_SETTLE_STATE_ENUM: SmartEnum<string> = {
  OPEN: {value: 'OPEN', desc: '未结清'},
  PARTIAL: {value: 'PARTIAL', desc: '部分结清'},
  SETTLED: {value: 'SETTLED', desc: '已结清'},
};

/**
 * 财务流水记录的动作类型（与后端 `ScmFinanceOperationTypeEnum` 逐字对应）。
 *
 * 数据库用 CHECK 约束固定取值，新增动作要两边同时改；
 * 展示层不许再直接把 `operationType` 原样印到表格里（曾出现 GENERATE / RED_GENERATE 直出）。
 */
export const SCM_FINANCE_OPERATION_TYPE_ENUM: SmartEnum<string> = {
  GENERATE: {value: 'GENERATE', desc: '生成'},
  RED_GENERATE: {value: 'RED_GENERATE', desc: '红字生成'},
  RECEIVE: {value: 'RECEIVE', desc: '收款登记'},
  PAY: {value: 'PAY', desc: '付款登记'},
  WRITE_OFF: {value: 'WRITE_OFF', desc: '核销'},
  WRITE_OFF_REVERSE: {value: 'WRITE_OFF_REVERSE', desc: '核销撤销'},
  RECEIPT_REVERSE: {value: 'RECEIPT_REVERSE', desc: '收款反向'},
  PAYMENT_REVERSE: {value: 'PAYMENT_REVERSE', desc: '付款反向'},
  WAIVER_ADD: {value: 'WAIVER_ADD', desc: '抹零登记'},
  WAIVER_REVERSE: {value: 'WAIVER_REVERSE', desc: '抹零反向'},
  POSTING_ADD: {value: 'POSTING_ADD', desc: '过账登记'},
  POSTING_UPDATE: {value: 'POSTING_UPDATE', desc: '过账修改'},
  POSTING_VOID: {value: 'POSTING_VOID', desc: '过账作废'},
};

/** 付款方式（与后端 `ScmFinancePaymentMethodEnum` 逐字对应）。 */
export const SCM_FINANCE_PAYMENT_METHOD_ENUM: SmartEnum<string> = {
  CASH: {value: 'CASH', desc: '现金'},
  BANK_TRANSFER: {value: 'BANK_TRANSFER', desc: '银行转账'},
  OTHER: {value: 'OTHER', desc: '其他'},
};

/**
 * 收款方式（与后端 `ScmFinanceReceiptMethodEnum` 逐字对应）。
 *
 * 与付款方式分开：在线支付只在收款侧出现。
 * 没有 COD —— 它是结算时机，不是收款渠道。
 * 也没有 BALANCE —— 余额消费没有资金进入，不算收款。
 */
export const SCM_FINANCE_RECEIPT_METHOD_ENUM: SmartEnum<string> = {
  CASH: {value: 'CASH', desc: '现金'},
  BANK_TRANSFER: {value: 'BANK_TRANSFER', desc: '银行转账'},
  ONLINE_PAYMENT: {value: 'ONLINE_PAYMENT', desc: '在线支付'},
  OTHER: {value: 'OTHER', desc: '其他'},
};

/**
 * 客户退款的付款方式（与后端 `ScmFinanceCustomerRefundMethodEnum` 逐字对应）。
 *
 * 与付款方式刻意分开：`ONLINE_PAYMENT` 只对客户退款放行（后端 CHECK 按对手方分组），
 * 放进供应商付款的选项里会造出「前端能选、后端必然失败」。
 */
export const SCM_FINANCE_CUSTOMER_REFUND_METHOD_ENUM: SmartEnum<string> = {
  CASH: {value: 'CASH', desc: '现金'},
  BANK_TRANSFER: {value: 'BANK_TRANSFER', desc: '银行转账'},
  ONLINE_PAYMENT: {value: 'ONLINE_PAYMENT', desc: '在线支付'},
  OTHER: {value: 'OTHER', desc: '其他'},
};

export const SCM_FINANCE_COUNTERPARTY_TYPE_ENUM: SmartEnum<string> = {
  CUSTOMER: {value: 'CUSTOMER', desc: '客户'},
  SUPPLIER: {value: 'SUPPLIER', desc: '供应商'},
};

export const SCM_FINANCE_SOURCE_TYPE_ENUM: SmartEnum<string> = {
  RECEIPT: {value: 'RECEIPT', desc: '收款'},
  PAYMENT: {value: 'PAYMENT', desc: '付款'},
};

/** 应收/应付明细行的来源单据类型：明细行只带来源主键，展示时要换成这层业务含义。 */
export const SCM_FINANCE_ITEM_SOURCE_TYPE_ENUM: SmartEnum<string> = {
  INVENTORY_OUTBOUND_ITEM: {value: 'INVENTORY_OUTBOUND_ITEM', desc: '库存出库明细'},
  ORDER_RETURN_ITEM: {value: 'ORDER_RETURN_ITEM', desc: '销售退货明细'},
  PURCHASE_RECEIPT_ITEM: {value: 'PURCHASE_RECEIPT_ITEM', desc: '采购收货明细'},
  MANUAL: {value: 'MANUAL', desc: '手工登记'},
};

export const SCM_FINANCE_BUSINESS_TYPE_ENUM: SmartEnum<string> = {
  RECEIVABLE: {value: 'RECEIVABLE', desc: '应收'},
  PAYABLE: {value: 'PAYABLE', desc: '应付'},
  RECEIPT: {value: 'RECEIPT', desc: '收款'},
  PAYMENT: {value: 'PAYMENT', desc: '付款'},
  WRITE_OFF: {value: 'WRITE_OFF', desc: '核销'},
  WAIVER: {value: 'WAIVER', desc: '抹零'},
  POSTING: {value: 'POSTING', desc: '过账'},
};

/** 过账单据的有效状态（由作废事实推导，前端只读）。 */
export const SCM_FINANCE_POSTING_STATUS_ENUM: SmartEnum<string> = {
  NORMAL: {value: 'NORMAL', desc: '有效'},
  VOIDED: {value: 'VOIDED', desc: '已作废'},
};

/** 过账单据的返款状态（与后端 `ScmFinancePostingRefundStatusEnum` 逐字对应）。 */
export const SCM_FINANCE_POSTING_REFUND_STATUS_ENUM: SmartEnum<string> = {
  PENDING: {value: 'PENDING', desc: '未返款'},
  PARTIAL: {value: 'PARTIAL', desc: '部分返款'},
  REFUNDED: {value: 'REFUNDED', desc: '已返款'},
};

/** Stable DOM ids for browser checks; column settings use the numeric ids below. */
export const SCM_FINANCE_DOM_TABLE_ID = {
  RECEIVABLE: 'scm-finance-receivable-table',
  PAYABLE: 'scm-finance-payable-table',
  RECEIPT: 'scm-finance-receipt-table',
  PAYMENT: 'scm-finance-payment-table',
  WRITE_OFF: 'scm-finance-write-off-table',
  POSTING: 'scm-finance-posting-table',
} as const;

export const SCM_FINANCE_ENTRY_COLOR: Record<string, string> = {
  NORMAL: 'blue',
  RED: 'red',
  REVERSE: 'orange',
};

export default {
  SCM_FINANCE_ENTRY_TYPE_ENUM,
  SCM_FINANCE_SETTLE_STATE_ENUM,
  SCM_FINANCE_OPERATION_TYPE_ENUM,
  SCM_FINANCE_PAYMENT_METHOD_ENUM,
  SCM_FINANCE_CUSTOMER_REFUND_METHOD_ENUM,
  SCM_FINANCE_RECEIPT_METHOD_ENUM,
  SCM_FINANCE_COUNTERPARTY_TYPE_ENUM,
  SCM_FINANCE_SOURCE_TYPE_ENUM,
  SCM_FINANCE_BUSINESS_TYPE_ENUM,
  SCM_FINANCE_POSTING_STATUS_ENUM,
  SCM_FINANCE_POSTING_REFUND_STATUS_ENUM,
};
