/**
 * 审计日志中的枚举码按字段和业务类型转换成中文。
 * 同一个值在不同模块可能含义不同，不能仅按值翻译；未知值保留原文。
 */
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

/** 后写的覆盖先写的；只用于语义确实一致的枚举合并。 */
function merge(...enums: ValueEnum[]): ValueEnum {
  return Object.assign({}, ...enums);
}

/**
 * 应收单头的来源类型，与后端 `ScmFinanceReceivableSourceTypeEnum` 对应；
 * 另两个码来自核销侧的 `SCM_FINANCE_SOURCE_TYPE_ENUM`，码不重叠故合并。
 */
const RECEIVABLE_SOURCE_ENUM: ValueEnum = {
  SALES_ORDER: {value: 'SALES_ORDER', desc: '销售订单'},
  ORDER_RETURN: {value: 'ORDER_RETURN', desc: '销售退货'},
  RECEIPT: {value: 'RECEIPT', desc: '收款'},
  PAYMENT: {value: 'PAYMENT', desc: '付款'},
};

/**
 * 财务各单的 `sourceType` 每域一套枚举（应收 / 应付 / 收款 / 付款 / 核销 / 明细行），
 * 码跨域不冲突 —— 唯一交集 `MANUAL` 在应付单头与应付明细都是「手工登记」，
 * 因此合成一张表按共用键名查。
 */
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

/**
 * 核销行的目标侧类型，与后端 `ScmFinanceWriteOffTargetTypeEnum` 对应；
 * 码虽与业务类型枚举重合，语义却是「核销指向哪侧单据」，单独声明不把整张业务类型枚举拖进审计映射。
 */
const WRITE_OFF_TARGET_TYPE_ENUM: ValueEnum = {
  RECEIVABLE: {value: 'RECEIVABLE', desc: '应收'},
  PAYABLE: {value: 'PAYABLE', desc: '应付'},
};

/**
 * 数量来源：订单明细的「实收来源」与采购收货的有效数量来源共用 `SYSTEM` / `MANUAL`，
 * 两域措辞一致（系统按申报量取值 / 人工按实重录入），故合并。
 */
const QUANTITY_SOURCE_ENUM: ValueEnum = {
  SYSTEM: {value: 'SYSTEM', desc: '系统取值'},
  MANUAL: {value: 'MANUAL', desc: '人工录入'},
};

/**
 * 退货 / 退款单状态里与销售、采购状态不冲突的那几个码。
 *
 * `PENDING` 刻意不并进来：它在销售订单是「待确认」、在退货单是「待审核」、在退款单是「待退款」，
 * 按值翻必然翻错 —— 这三个只能靠调用方给的 `scope` 区分，见 `SCOPED_ENUM_BY_FIELD`。
 */
const RETURN_REFUND_STATUS_ENUM: ValueEnum = {
  APPROVED: {value: 'APPROVED', desc: '已批准'},
  REJECTED: {value: 'REJECTED', desc: '已驳回'},
  COMPLETED: {value: 'COMPLETED', desc: '已完成'},
};

/**
 * 字段名 → 该字段的取值枚举。
 *
 * `status` 合并订单 / 采购 / 收货 / 退货 / 退款五套里「互不冲突」的码：
 * `DRAFT`、`CANCELLED` 措辞一致，其余码各自独有，因此合并不会翻错。刻意不并入「入库状态」——
 * 它的 `PENDING` 是「待入库」，与订单的「待确认」冲突。
 */
const ENUM_BY_FIELD: Record<string, ValueEnum> = {
  status: merge(SCM_ORDER_STATUS_ENUM, SCM_PURCHASE_STATUS_ENUM, SCM_RECEIPT_STATUS_ENUM, RETURN_REFUND_STATUS_ENUM),
  orderStatus: SCM_PURCHASE_STATUS_ENUM,
  orderSource: SCM_ORDER_SOURCE_ENUM,
  settleModeSnapshot: SETTLE_MODE_ENUM,
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

/** 审计日志中的状态码按单据类型转换为中文。例如退货待审核和退款待退款不能只按 PENDING 统一翻译。 */
const SCOPED_ENUM_BY_FIELD: Record<string, Record<string, ValueEnum>> = {
  RETURN: {status: SCM_ORDER_RETURN_STATUS_ENUM},
  REFUND: {status: SCM_ORDER_REFUND_STATUS_ENUM},
};

/**
 * 取字段取值的中文名；字段不在枚举表内、或该字段认不出这个值时，原样返回。
 *
 * `scope` 是这条日志的操作类型（`RETURN` / `REFUND`…），只有同名字段跨单据含义不同时才需要传。
 */
export function auditValueLabel(key: string, value: string, scope?: string): string {
  const scoped = scope ? SCOPED_ENUM_BY_FIELD[scope]?.[key] : undefined;
  return (scoped ?? ENUM_BY_FIELD[key])?.[value]?.desc ?? value;
}
