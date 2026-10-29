/** 报表常量只维护权限和展示配置；订单、库存、采购类型复用原模块的枚举，避免复制出不一致的状态表。 */
import type {SmartEnum} from '/@/types/smart-enum';

/** 损耗分析只算盘亏和报损；盘盈、增益、销售出库都不计损耗成本。 */
export const SCM_REPORT_LOSS_TYPE_ENUM: SmartEnum<string> = {
  STOCKTAKE_LOSS: {value: 'STOCKTAKE_LOSS', desc: '盘亏'},
  LOSS_REPORT: {value: 'LOSS_REPORT', desc: '报损'},
};

/** 报表导出需同时具备页面查询和导出权限；成本字段需要独立成本权限。无权查看的成本不返回数值，也不能显示为零。 */
export const SCM_REPORT_PERMISSION = {
  INVENTORY_SNAPSHOT_CREATE: 'scm:report:inventory:snapshot:create',
  OVERVIEW_QUERY: 'scm:report:overview:query',
  SALES_QUERY: 'scm:report:sales:query',
  ORDER_EXCEPTION_QUERY: 'scm:report:order:exception:query',
  PURCHASE_QUERY: 'scm:report:purchase:query',
  INVENTORY_QUERY: 'scm:report:inventory:query',
  FINANCE_QUERY: 'scm:report:finance:query',
  FINANCE_AGING_QUERY: 'scm:report:finance:aging:query',
  FINANCE_PROFIT_QUERY: 'scm:report:finance:profit:query',
  CUSTOMER_STATEMENT_QUERY: 'scm:report:customer:statement:query',
  CUSTOMER_STATEMENT_FREEZE: 'scm:report:customer:statement:freeze',
  SUPPLIER_STATEMENT_QUERY: 'scm:report:supplier:statement:query',
  SUPPLIER_STATEMENT_FREEZE: 'scm:report:supplier:statement:freeze',
  COST_QUERY: 'scm:report:cost:query',
  EXPORT: 'scm:report:export',
} as const;

/**
 * 表格 DOM id —— 给 Playwright 定位用，不是 `TableOperator` 的 `tableId`。
 *
 * `TableOperator` 的 `tableId` 是数字（列配置持久化键），注册在
 * `TABLE_ID_CONST.BUSINESS.SCM_REPORT_*`；两者用途不同，沿用库存域的做法分开声明。
 */
export const SCM_REPORT_TABLE_ID = {
  OVERVIEW_DAILY: 'scm-report-overview-daily-table',
  SALES_PRODUCT: 'scm-report-sales-product-table',
  SALES_CATEGORY: 'scm-report-sales-category-table',
  SALES_CUSTOMER: 'scm-report-sales-customer-table',
  SALES_SELLER: 'scm-report-sales-seller-table',
  SALES_ITEM: 'scm-report-sales-item-table',
  /** 订单表头级（一行 = 一个订单），与行级 `SALES_ITEM` 是两张表，DOM id 不能相同。 */
  SALES_ORDER: 'scm-report-sales-order-table',
  PURCHASE_PRODUCT: 'scm-report-purchase-product-table',
  PURCHASE_SUPPLIER: 'scm-report-purchase-supplier-table',
  PURCHASE_PURCHASER: 'scm-report-purchase-purchaser-table',
  PURCHASE_ITEM: 'scm-report-purchase-item-table',
  PURCHASE_PRICE_TREND: 'scm-report-purchase-price-trend-table',
  /** 供应商 / 采购员下钻抽屉里的商品明细表：与页面主表不同 id，否则同一 DOM 里出现两个相同 id。 */
  PURCHASE_DRILLDOWN: 'scm-report-purchase-drilldown-table',
  RECEIPT: 'scm-report-receipt-table',
  INBOUND: 'scm-report-inbound-table',
  PENDING_PUTAWAY: 'scm-report-pending-putaway-table',
  INVENTORY_MOVEMENT: 'scm-report-inventory-movement-table',
  INVENTORY_LOSS: 'scm-report-inventory-loss-table',
  INVENTORY_VALUE: 'scm-report-inventory-value-table',
  INVENTORY_FLOW_SUMMARY: 'scm-report-inventory-flow-summary-table',
  INVENTORY_NEGATIVE_STOCK: 'scm-report-inventory-negative-stock-table',
  FINANCE_RECEIVABLE: 'scm-report-finance-receivable-table',
  FINANCE_PAYABLE: 'scm-report-finance-payable-table',
} as const;

export default {
  // 只导出枚举：`SCM_REPORT_TABLE_ID` / `SCM_REPORT_PERMISSION` 不是枚举，
  // 混进 `constantsInfo` 会让 `$smartEnumPlugin.getValueDescList` 拿到非枚举对象。
  SCM_REPORT_LOSS_TYPE_ENUM,
};
