/**
 * Finance R0 报表中心前端常量（新增文件）。
 *
 * 这里只放**报表中心自己**的东西：表格 DOM id、权限码、损耗类型口径。
 * 其余枚举一律复用既有单一来源，**不在本文件复制第二份**：
 *
 * ```text
 * 流水类型 / 来源单据类型  → inventory-const.ts 的 SCM_INVENTORY_MOVEMENT_TYPE_ENUM / SCM_INVENTORY_SOURCE_TYPE_ENUM
 * 收货模式 / 入库状态      → purchase-const.ts 的 SCM_RECEIPT_MODE_ENUM / SCM_PUTAWAY_STATUS_ENUM
 * 采购状态                 → purchase-const.ts 的 SCM_PURCHASE_STATUS_ENUM
 * 订单来源 / 价格来源      → order-const.ts 的 SCM_ORDER_SOURCE_ENUM / SCM_ORDER_PRICE_SOURCE_ENUM
 * ```
 *
 * 复制一份枚举值等于制造第二个真相：后端扩枚举时只改一处，另一份会**静默陈旧**，
 * 表现是筛选下拉里少一个类型、而列表里那一行的类型列显示成原值。
 */
import type {SmartEnum} from '/@/types/smart-enum';

/**
 * 损耗分析口径下的流水类型子集。
 *
 * **报表侧的分类，不是库存侧的枚举**：计划 §23 明确 R0 只承认「盘亏」与「手工报损」两类
 * 可证明的损耗事实，`STOCKTAKE_GAIN` / `GAIN_REPORT` 是增益、`SALES_OUT` 是履约，
 * 都不进损耗成本。因此这个子集属于报表中心，放在这里而不是去改库存枚举。
 */
export const SCM_REPORT_LOSS_TYPE_ENUM: SmartEnum<string> = {
    STOCKTAKE_LOSS: {value: 'STOCKTAKE_LOSS', desc: '盘亏'},
    LOSS_REPORT: {value: 'LOSS_REPORT', desc: '报损'},
};

/**
 * 报表中心权限码（与后端 `@SaCheckPermission` 逐字对应，计划 §32）。
 *
 * 三条约束：
 * - 导出 = 对应页的 query 权限 **AND** `export`；`v-privilege` 只吃一个码，
 *   所以按钮上挂 `EXPORT`，AND 关系由后端裁决；
 * - `COST_QUERY` 是**独立的成本门禁**：没有它的角色可以看数量流水，不能看均价与金额；
 * - 后端已对缺权限的调用者把成本字段置 `null`，前端只需把列隐藏并把 `null` 渲染成 `—`，
 *   **不得**在前端做「无权限就显示 0」。
 */
export const SCM_REPORT_PERMISSION = {
    OVERVIEW_QUERY: 'scm:report:overview:query',
    SALES_QUERY: 'scm:report:sales:query',
    PURCHASE_QUERY: 'scm:report:purchase:query',
    INVENTORY_QUERY: 'scm:report:inventory:query',
    COST_QUERY: 'scm:report:cost:query',
    EXPORT: 'scm:report:export',
} as const;

/**
 * 表格 DOM id —— **给 Playwright 定位用**，不是 `TableOperator` 的 `tableId`。
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
} as const;

export default {
    // 只导出**枚举**：`SCM_REPORT_TABLE_ID` / `SCM_REPORT_PERMISSION` 不是枚举，
    // 混进 `constantsInfo` 会让 `$smartEnumPlugin.getValueDescList` 拿到非枚举对象。
    SCM_REPORT_LOSS_TYPE_ENUM,
};
