/** 审计日志枚举值转中文；未知值保留原文。 */
import type {SmartEnum} from '/@/types/smart-enum';
import {
  SCM_ORDER_PRICE_SOURCE_ENUM,
  SCM_ORDER_REFUND_STATUS_ENUM,
  SCM_ORDER_RETURN_STATUS_ENUM,
  SCM_ORDER_SOURCE_ENUM,
  SCM_ORDER_STATUS_ENUM,
} from '/@/constants/business/scm/order-const';
import {SCM_PURCHASE_STATUS_ENUM, SCM_RECEIPT_STATUS_ENUM} from '/@/constants/business/scm/purchase-const';
import {
  SCM_FINANCE_COUNTERPARTY_TYPE_ENUM,
  SCM_FINANCE_ENTRY_TYPE_ENUM,
  SCM_FINANCE_PAYMENT_METHOD_ENUM,
  SCM_FINANCE_RECEIPT_METHOD_ENUM,
} from '/@/constants/business/scm/finance-const';
import {SETTLE_MODE_ENUM} from '/@/constants/business/scm/customer-const';
import {SCM_SORTING_PRODUCT_TYPE_ENUM} from '/@/constants/business/scm/sorting-const';

type ValueEnum = SmartEnum<string>;

/** 合并枚举，后写的覆盖先写的。 */
function merge(...enums: ValueEnum[]): ValueEnum {
  return Object.assign({}, ...enums);
}

/** 应收单头的来源类型，与后端枚举对应。 */
const RECEIVABLE_SOURCE_ENUM: ValueEnum = {
  SALES_ORDER: {value: 'SALES_ORDER', desc: '销售订单'},
  ORDER_RETURN: {value: 'ORDER_RETURN', desc: '销售退货'},
  RECEIPT: {value: 'RECEIPT', desc: '收款'},
  PAYMENT: {value: 'PAYMENT', desc: '付款'},
};

/** 财务各单的来源类型；码不重复，合并成一张表。 */
const FINANCE_SOURCE_TYPE_ENUM: ValueEnum = merge(RECEIVABLE_SOURCE_ENUM, {
  BALANCE_MOVEMENT: {value: 'BALANCE_MOVEMENT', desc: '余额消费'},
  PURCHASE_RECEIPT: {value: 'PURCHASE_RECEIPT', desc: '采购收货单'},
  PAYMENT_TRANSACTION: {value: 'PAYMENT_TRANSACTION', desc: '支付交易'},
  ORDER_REFUND: {value: 'ORDER_REFUND', desc: '订单退款'},
  INVENTORY_OUTBOUND_ITEM: {value: 'INVENTORY_OUTBOUND_ITEM', desc: '库存出库明细'},
  ORDER_RETURN_ITEM: {value: 'ORDER_RETURN_ITEM', desc: '销售退货明细'},
  PURCHASE_RECEIPT_ITEM: {value: 'PURCHASE_RECEIPT_ITEM', desc: '采购收货明细'},
  MANUAL: {value: 'MANUAL', desc: '手工登记'},
});

/** 核销指向的类型：应收或应付。 */
const WRITE_OFF_TARGET_TYPE_ENUM: ValueEnum = {
  RECEIVABLE: {value: 'RECEIVABLE', desc: '应收'},
  PAYABLE: {value: 'PAYABLE', desc: '应付'},
};

/** 数量来源：系统取值或人工录入。 */
const QUANTITY_SOURCE_ENUM: ValueEnum = {
  SYSTEM: {value: 'SYSTEM', desc: '系统取值'},
  MANUAL: {value: 'MANUAL', desc: '人工录入'},
};

/** 退货/退款共用状态码；PENDING 各域含义不同，按 scope 区分。 */
const RETURN_REFUND_STATUS_ENUM: ValueEnum = {
  APPROVED: {value: 'APPROVED', desc: '已批准'},
  REJECTED: {value: 'REJECTED', desc: '已驳回'},
  COMPLETED: {value: 'COMPLETED', desc: '已完成'},
};

/** 字段名对应的枚举；status 只合并各域中含义一致的码。 */
const ENUM_BY_FIELD: Record<string, ValueEnum> = {
  status: merge(SCM_ORDER_STATUS_ENUM, SCM_PURCHASE_STATUS_ENUM, SCM_RECEIPT_STATUS_ENUM, RETURN_REFUND_STATUS_ENUM),
  orderStatus: SCM_PURCHASE_STATUS_ENUM,
  orderSource: SCM_ORDER_SOURCE_ENUM,
  settleModeSnapshot: SETTLE_MODE_ENUM,
  settlementMethod: {
    WALLET: {value: 'WALLET', desc: '余额结算'},
    CREDIT: {value: 'CREDIT', desc: '账期结算'},
    COD: {value: 'COD', desc: '货到付款'},
  },
  productTypeSnapshot: SCM_SORTING_PRODUCT_TYPE_ENUM,
  draftPriceSource: SCM_ORDER_PRICE_SOURCE_ENUM,
  lockedPriceSource: SCM_ORDER_PRICE_SOURCE_ENUM,
  actualQuantitySource: QUANTITY_SOURCE_ENUM,
  entryType: SCM_FINANCE_ENTRY_TYPE_ENUM,
  counterpartyType: SCM_FINANCE_COUNTERPARTY_TYPE_ENUM,
  sourceType: FINANCE_SOURCE_TYPE_ENUM,
  targetType: WRITE_OFF_TARGET_TYPE_ENUM,
  method: merge(SCM_FINANCE_RECEIPT_METHOD_ENUM, SCM_FINANCE_PAYMENT_METHOD_ENUM),
};

/** 同名字段在不同单据下含义不同时，按操作类型取对应枚举。 */
const SCOPED_ENUM_BY_FIELD: Record<string, Record<string, ValueEnum>> = {
  RETURN: {status: SCM_ORDER_RETURN_STATUS_ENUM},
  REFUND: {status: SCM_ORDER_REFUND_STATUS_ENUM},
};

/** 返回字段值的中文名；未知值原样返回。scope 用于区分同名字段的不同含义。 */
export function auditValueLabel(key: string, value: string, scope?: string): string {
  const scoped = scope ? SCOPED_ENUM_BY_FIELD[scope]?.[key] : undefined;
  return (scoped ?? ENUM_BY_FIELD[key])?.[value]?.desc ?? value;
}
