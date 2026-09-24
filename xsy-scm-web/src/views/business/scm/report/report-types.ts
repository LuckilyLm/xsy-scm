/**
 * Finance R0 报表中心前端类型（新增文件）。
 *
 * 字段与后端 `module/scm/report/domain/vo` 的 VO **逐字对齐**，命名即口径：
 *
 * - 这里**不存在**「营业收入」「已收款」「应收」「应付」「毛利」这类字段。R0 没有签收、
 *   应收、应付与核销事实，把已确认订单金额叫成收入会把「承诺」说成「已实现」；
 * - 销售侧只有 `CONFIRMED + confirmed_at + settlement_*`；采购侧只有提交后的采购事实；
 * - 定点数一律是 `string | null`（后端 `ScmFixedScale4Serializer`：`null → JSON null`、
 *   `0 → "0.0000"`）。**`null` 是「没有这个事实」，与 `"0.0000"` 是两种不同的东西**，
 *   所以类型上不写 `number`，也不允许前端 `Number()` 后再算；
 * - 计数字段（`Long` / `Integer`）是 JSON 数字，与定点数区分，因此类型是 `number | null`；
 * - 时间是字符串，由 `common/scm-display` 的 `datetime` 直接渲染，不做二次时区换算。
 *
 * 库存分析四张表（`InventoryReportVO` 各内部类）在写这份类型时后端尚未落盘，
 * 字段按计划 §22–§25 的列清单 + 既有 `inventory-types.ts` 的同名字段推得，
 * 并刻意与 `InventoryMovement` 的命名保持一致（`unitSnapshot` / `sourceDocumentNo`），
 * 避免同一事实出现两套字段名。后端落盘后如出现差异，以**后端为准并改这里**。
 */
import type {ScmPage} from '/@/types/business/scm/customer';

/** 后端主键是 `Long`，经 JSON 数字或字符串抵达前端；一律不参与算术。 */
export type ReportId = string | number;

/** 分页入参：`pageNum` / `pageSize` 必填（后端 `PageParam` 为 null 时 `convert2PageQuery` 会 NPE）。 */
export interface ReportPage {
    pageNum: number;
    pageSize: number;
}

/**
 * 报表查询的公共日期段（Asia/Shanghai 日界的**闭区间**）。
 *
 * 后端把它转成 `[startDate 00:00, endDate+1 00:00)` 的半开区间，前端不参与这个换算。
 */
export interface ReportDateQuery {
    startDate?: string;
    endDate?: string;
}

// ==================================================================
// 图表数据形状（不是后端契约：由页面从契约行映射而来）
// ==================================================================

/**
 * TOP 排名条形图的一行。
 *
 * `value` 只决定条长（由 `report-model.chartValue` 从后端定点字符串安全转换，
 * `null` 保持 `null`）；`text` 是后端原文，tooltip 与条末标签都只用它，
 * 这样图上的数字与表格那一行**位数完全一致**。
 */
export interface ReportChartBar {
    name: string;
    value: number | null;
    text: string;
}

/**
 * 折线图的一条线：`data` 与 `texts` 等长。
 *
 * 两套值的理由与条形图相同 —— 折线要数字才能画，展示必须是原样字符串。
 */
export interface ReportChartLine {
    name: string;
    data: Array<number | null>;
    texts: string[];
}

/** 饼图的一片。 */
export interface ReportChartSlice {
    name: string;
    value: number | null;
    text: string;
}

// ==================================================================
// 经营概览
// ==================================================================

/** `ReportOverviewVO`：概览指标卡。 */
export interface ReportOverview {
    /** `status=CONFIRMED` 且 `confirmed_at` 落在区间内的订单数。 */
    confirmedOrderCount?: number | null;
    /** 同一确认范围内的去重下单客户数。 */
    customerCount?: number | null;
    /** 「已确认订单金额」：`SUM(settlement_total_amount)`，结算口径而非下单口径。 */
    confirmedOrderAmount?: string | null;
    /** `order_refund` 中 `COMPLETED` 的退款额；独立展示，不冲减订单金额。 */
    completedRefundAmount?: string | null;
    submittedPurchaseOrderCount?: number | null;
    /** 已提交（含部分收货 / 已收货 / 短关）采购单的 `SUM(total_amount)`。 */
    submittedPurchaseAmount?: string | null;
    /** `PURCHASE_IN` 流水的 `SUM(quantity * unit_cost)`。 */
    purchaseInCostAmount?: string | null;
    /** > 0 表示 {@link purchaseInCostAmount} 是**不完整**的和，页面必须显性提示。 */
    purchaseInCostMissingCount?: number | null;
    /** 当前库存账面金额：`SUM(inventory_balance.quantity * avg_cost)`，**不受查询区间影响**。 */
    inventoryBookValue?: string | null;
    /** 当前有账面库存的余额行数。 */
    stockedSkuCount?: number | null;
    /** {@link inventoryBookValue} 的取值时点；必须与金额一起读才是完整事实。 */
    snapshotAt?: string | null;
}

/** `ReportDailyStatVO`：按业务日聚合的一行，同时服务趋势折线与每日统计表。 */
export interface ReportDailyStat {
    /** 业务日期 `yyyy-MM-dd`（Asia/Shanghai）。日期轴由 SQL 补齐，没有单据的天也在。 */
    bizDate: string;
    confirmedOrderCount?: number | null;
    customerCount?: number | null;
    confirmedOrderAmount?: string | null;
    completedRefundAmount?: string | null;
    submittedPurchaseAmount?: string | null;
    purchaseInCostAmount?: string | null;
    purchaseInCostMissingCount?: number | null;
}

/** `ScmOverviewReportQueryForm`：概览 / 趋势 / 每日统计共用同一条件。 */
export interface OverviewQuery extends ReportDateQuery {
    warehouseId?: ReportId;
    customerId?: ReportId;
    /** 员工 id：选择器组件的取值是 `number`，这里不用 `ReportId` 以免绑定处需要断言。 */
    sellerId?: number;
    orderSource?: string;
    categoryId?: ReportId;
    keyword?: string;
}

// ==================================================================
// 销售分析
// ==================================================================

/** `SalesReportVO.TopItem`：通用 TOP N 行。 */
export interface SalesTopItem {
    id?: ReportId | null;
    name?: string | null;
    amount?: string | null;
}

/**
 * 供应商采购入库成本 TOP N 行。
 *
 * 形状与 {@link SalesTopItem} 相同（`id` / `name` / `amount`）。后端 `PurchaseReportVO`
 * 目前**没有** `TopItem` 内部类，计划口径是复用同一个通用 TOP 行；若最终落为独立类，
 * 只需把本别名换成对应字段，页面不受影响。
 */
export type PurchaseTopItem = SalesTopItem;

/** `SalesReportVO.ProductRow`：粒度 = SKU × 销售单位快照。 */
export interface SalesProductRow {
    skuId?: ReportId;
    spuCode?: string | null;
    productName?: string | null;
    skuCode?: string | null;
    /** 订单行上的规格快照，不实时回查商品主档。 */
    specName?: string | null;
    saleUnit?: string | null;
    rootCategoryName?: string | null;
    leafCategoryName?: string | null;
    orderCount?: number | null;
    customerCount?: number | null;
    /** `SUM(actual_quantity)`：确认数量；未回写实量的行为 null，不计入。 */
    confirmedQuantity?: string | null;
    /** 成交均价 = 结算金额 / 确认数量（同单位内）。数量为 0 或 null 时是 null 而非 0。 */
    avgTransactionPrice?: string | null;
    settlementAmount?: string | null;
    /** 金额排名由 SQL 窗口函数在**分页之前**算出，所以第 2 页仍是全局排名。 */
    amountRank?: number | null;
}

/** `SalesReportVO.CategoryRow`：粒度 = 末级分类。 */
export interface SalesCategoryRow {
    categoryId?: ReportId;
    rootCategoryName?: string | null;
    leafCategoryName?: string | null;
    settlementAmount?: string | null;
    orderCount?: number | null;
    customerCount?: number | null;
    amountRank?: number | null;
}

/** `SalesReportVO.CustomerRow`：粒度 = 客户。 */
export interface SalesCustomerRow {
    customerId?: ReportId;
    customerCode?: string | null;
    customerName?: string | null;
    sellerName?: string | null;
    orderCount?: number | null;
    skuKindCount?: number | null;
    settlementAmount?: string | null;
    completedRefundAmount?: string | null;
    lastConfirmedAt?: string | null;
    amountRank?: number | null;
}

/** `SalesReportVO.SellerRow`：粒度 = 销售员；`seller_id` 为空归入「未分配销售员」。 */
export interface SalesSellerRow {
    sellerId?: ReportId | null;
    sellerName?: string | null;
    orderCount?: number | null;
    customerCount?: number | null;
    settlementAmount?: string | null;
    completedRefundAmount?: string | null;
    lastConfirmedAt?: string | null;
}

/** `SalesReportVO.ItemRow`：粒度 = `sales_order_item`，业务字段全部取订单行快照。 */
export interface SalesItemRow {
    orderId?: ReportId;
    orderItemId?: ReportId;
    orderNo?: string | null;
    confirmedAt?: string | null;
    customerCode?: string | null;
    customerName?: string | null;
    sellerName?: string | null;
    orderSource?: string | null;
    settleMode?: string | null;
    spuCode?: string | null;
    productName?: string | null;
    skuCode?: string | null;
    specName?: string | null;
    productType?: string | null;
    saleUnit?: string | null;
    orderedQuantity?: string | null;
    actualQuantity?: string | null;
    /** 确认时锁定的成交单价。 */
    lockedUnitPrice?: string | null;
    lockedPriceSource?: string | null;
    settlementLineAmount?: string | null;
    manualPriceOverride?: boolean | null;
    manualPriceReason?: string | null;
}

/** `ScmSalesReportQueryForm`：五个维度共用一个表单（切 Tab 不串条件是前端职责）。 */
export interface SalesQuery extends ReportPage, ReportDateQuery {
    customerId?: ReportId;
    /** 员工 id，见 {@link OverviewQuery.sellerId} 的理由。 */
    sellerId?: number;
    orderSource?: string;
    categoryId?: ReportId;
    keyword?: string;
}

// ==================================================================
// 采购分析
// ==================================================================

/** `PurchaseReportVO.Overview`：采购概览指标卡。 */
export interface PurchaseOverview {
    submittedOrderCount?: number | null;
    submittedAmount?: string | null;
    confirmedReceiptCount?: number | null;
    /** 收货数量 × 采购单价，只用于对价格与数量的交叉核对；**不叫应付金额**。 */
    receiptReferenceAmount?: string | null;
    purchaseInCostAmount?: string | null;
    purchaseInCostMissingCount?: number | null;
    /** `WAREHOUSE_CONFIRM + CONFIRMED + PENDING` 的收货单数。 */
    pendingPutawayReceiptCount?: number | null;
}

/** `PurchaseReportVO.ProductRow`：粒度 = SKU × 采购单位快照。 */
export interface PurchaseProductRow {
    skuId?: ReportId;
    spuCode?: string | null;
    productName?: string | null;
    skuCode?: string | null;
    skuName?: string | null;
    purchaseUnit?: string | null;
    orderCount?: number | null;
    plannedQuantity?: string | null;
    receivedQuantity?: string | null;
    orderAmount?: string | null;
    /** 采购成交均价 = 采购行金额合计 / 计划数量合计，不是若干单价的算术平均。 */
    avgPurchasePrice?: string | null;
    /** 入库数量以文本表达（按库存记账单位分组）：与采购单位可能不同，相加无量纲。 */
    inboundQuantityText?: string | null;
    inboundCostAmount?: string | null;
    inboundCostMissingCount?: number | null;
}

/** `PurchaseReportVO.SupplierRow`：粒度 = 供应商。 */
export interface PurchaseSupplierRow {
    supplierId?: ReportId;
    supplierCode?: string | null;
    supplierName?: string | null;
    orderCount?: number | null;
    skuKindCount?: number | null;
    orderAmount?: string | null;
    receiptReferenceAmount?: string | null;
    inboundCostAmount?: string | null;
    inboundCostMissingCount?: number | null;
    lastSubmittedAt?: string | null;
    amountRank?: number | null;
}

/** `PurchaseReportVO.PurchaserRow`：粒度 = 采购员。 */
export interface PurchasePurchaserRow {
    purchaserId?: ReportId;
    purchaserName?: string | null;
    orderCount?: number | null;
    skuKindCount?: number | null;
    orderAmount?: string | null;
    receiptReferenceAmount?: string | null;
    inboundCostAmount?: string | null;
    inboundCostMissingCount?: number | null;
    lastSubmittedAt?: string | null;
}

/** `PurchaseReportVO.ItemRow`：粒度 = `purchase_order_item`。 */
export interface PurchaseItemRow {
    purchaseOrderId?: ReportId;
    purchaseOrderItemId?: ReportId;
    orderNo?: string | null;
    submittedAt?: string | null;
    status?: string | null;
    supplierName?: string | null;
    purchaserName?: string | null;
    warehouseName?: string | null;
    plannedArrivalDate?: string | null;
    spuCode?: string | null;
    productName?: string | null;
    skuCode?: string | null;
    skuName?: string | null;
    purchaseUnit?: string | null;
    plannedQuantity?: string | null;
    receivedQuantity?: string | null;
    purchasePrice?: string | null;
    lineAmount?: string | null;
}

/** `PurchaseReportVO.PriceTrendPoint`：粒度 = 业务日 × SKU × 采购单位。 */
export interface PurchasePriceTrendPoint {
    bizDate?: string | null;
    skuId?: ReportId;
    skuCode?: string | null;
    productName?: string | null;
    /** **不同单位永不合并成一条线**（箱价与公斤价混画没有意义）。 */
    purchaseUnit?: string | null;
    weightedAvgPrice?: string | null;
    sampleLineCount?: number | null;
}

/** `ScmPurchaseReportQueryForm`：六个维度共用（默认统计四个「已提交」状态，SQL 固定）。 */
export interface PurchaseQuery extends ReportPage, ReportDateQuery {
    supplierId?: ReportId;
    /** 员工 id，见 {@link OverviewQuery.sellerId} 的理由。 */
    purchaserId?: number;
    warehouseId?: ReportId;
    status?: string;
    keyword?: string;
    /** 价格波动维度：按 SKU 收窄，避免把不同单位的价格画进同一条线。 */
    skuId?: ReportId;
}

// ==================================================================
// 收货与入库
// ==================================================================

/** `ReceiptReportVO.ReceiptRow`：粒度 = `purchase_receipt_item`（收货确认这一事实）。 */
export interface ReceiptRow {
    receiptId?: ReportId;
    receiptItemId?: ReportId;
    receiptNo?: string | null;
    purchaseOrderNo?: string | null;
    purchaseOrderId?: ReportId;
    supplierName?: string | null;
    warehouseName?: string | null;
    /** `DIRECT` / `WAREHOUSE_CONFIRM`。 */
    receiptMode?: string | null;
    /** `PENDING` / `COMPLETED`，即库存入账状态；**不得把已确认收货显示成已入库**。 */
    putawayStatus?: string | null;
    confirmedAt?: string | null;
    spuCode?: string | null;
    productName?: string | null;
    skuCode?: string | null;
    skuName?: string | null;
    purchaseUnit?: string | null;
    receivedQuantity?: string | null;
    /** 该采购行在全部收货单上的累计已收量，**不应用于本行求和**。 */
    cumulativeReceivedQuantity?: string | null;
    remainingQuantity?: string | null;
    overReceiptQuantity?: string | null;
    receiptDifference?: string | null;
    purchasePrice?: string | null;
    /** 本次收货量 × 采购单价；命名固定「收货参考金额」。 */
    receiptReferenceAmount?: string | null;
}

/** `ReceiptReportVO.InboundRow`：粒度 = 一条 `PURCHASE_IN` 流水（库存入账这一事实）。 */
export interface InboundRow {
    movementId?: ReportId;
    occurredAt?: string | null;
    warehouseName?: string | null;
    receiptNo?: string | null;
    purchaseOrderNo?: string | null;
    supplierName?: string | null;
    productName?: string | null;
    skuCode?: string | null;
    unit?: string | null;
    quantity?: string | null;
    unitCost?: string | null;
    costAmount?: string | null;
    /** `unit_cost` 缺失或调用者无成本权限时为 true：页面显示 `—` 而不是 0。 */
    costMissing?: boolean | null;
    operator?: string | null;
}

/** `ReceiptReportVO.PendingPutawayRow`：粒度 = 一张待入库收货单。 */
export interface PendingPutawayRow {
    receiptId?: ReportId;
    receiptNo?: string | null;
    purchaseOrderNo?: string | null;
    purchaseOrderId?: ReportId;
    supplierName?: string | null;
    warehouseName?: string | null;
    confirmedAt?: string | null;
    skuKindCount?: number | null;
    /** 按采购单位分组后的数量文本（如 `12kg / 3箱`）；**刻意不是单个数字**。 */
    quantityText?: string | null;
}

/** `ScmReceiptReportQueryForm`：三个 Tab 共用。 */
export interface ReceiptQuery extends ReportPage, ReportDateQuery {
    supplierId?: ReportId;
    warehouseId?: ReportId;
    purchaseOrderId?: ReportId;
    keyword?: string;
    receiptMode?: string;
    putawayStatus?: string;
}

// ==================================================================
// 库存分析
// ==================================================================

/**
 * 库存流水报表行（计划 §22）。
 *
 * 方向不在字段里：`quantity` 恒为正，方向由 `movementType` 经
 * `SCM_INVENTORY_MOVEMENT_INBOUND_TYPES` 派生（计划禁止报表再维护一份 IN / OUT 硬编码）。
 */
export interface InventoryMovementRow {
    movementId?: ReportId;
    occurredAt?: string | null;
    warehouseId?: ReportId;
    warehouseCode?: string | null;
    warehouseName?: string | null;
    skuId?: ReportId;
    skuCode?: string | null;
    skuName?: string | null;
    productName?: string | null;
    movementType?: string | null;
    sourceDocumentType?: string | null;
    /** 来源单号：收货单号 / 出库单号 / 盘点单号等，后端 COALESCE 成一个展示列。 */
    sourceDocumentNo?: string | null;
    sourceDocumentId?: ReportId | null;
    sourceDocumentItemId?: ReportId | null;
    quantity?: string | null;
    /** 写入时冻结的记账单位快照。 */
    unitSnapshot?: string | null;
    unitCost?: string | null;
    /** `quantity × unit_cost`，由后端算；无成本或无成本权限时为 null。 */
    costAmount?: string | null;
    beforeQuantity?: string | null;
    afterQuantity?: string | null;
    operator?: string | null;
}

/** 损耗分析 KPI（计划 §23）。数量按单位分组，因此只有文本没有可加数字。 */
export interface InventoryLossSummary {
    /** 盘亏数量文本（按记账单位分组）。 */
    stocktakeLossQuantityText?: string | null;
    stocktakeLossCostAmount?: string | null;
    /** 报损数量文本（按记账单位分组）。 */
    lossReportQuantityText?: string | null;
    lossReportCostAmount?: string | null;
    /** 损耗总成本金额（后端聚合，前端不相加）。 */
    totalLossCostAmount?: string | null;
    /** 被跳过的无成本行数；> 0 表示上面的金额不完整。 */
    missingCostCount?: number | null;
    /**
     * 按日损耗成本趋势（计划 §23 的「损耗金额按日趋势」）。
     *
     * 后端未提供该聚合时这里是 `undefined`，页面显示图表空态而**不在前端把明细行相加**：
     * 明细是分页的，拿一页去代表整个区间会画出一张错的图。
     */
    dailyTrend?: InventoryLossTrendPoint[] | null;
}

/** 损耗按日趋势点（业务日由后端补齐，与概览日统计同一口径）。 */
export interface InventoryLossTrendPoint {
    bizDate?: string | null;
    stocktakeLossCostAmount?: string | null;
    lossReportCostAmount?: string | null;
    totalLossCostAmount?: string | null;
}

/** 损耗明细行（计划 §23 表列）。 */
export interface InventoryLossRow {
    movementId?: ReportId;
    productName?: string | null;
    skuCode?: string | null;
    skuName?: string | null;
    warehouseName?: string | null;
    /** `STOCKTAKE_LOSS` 盘亏 / `LOSS_REPORT` 报损。 */
    movementType?: string | null;
    quantity?: string | null;
    unitSnapshot?: string | null;
    unitCost?: string | null;
    costAmount?: string | null;
    sourceDocumentNo?: string | null;
    occurredAt?: string | null;
    operator?: string | null;
}

/**
 * 当前库存价值行（计划 §24）。
 *
 * **当前时点值**：不受查询区间影响，页面必须标注「当前时点」，日期筛选在该 Tab 下失效。
 * 成本列受 `scm:report:cost:query` 控制，后端已对无权限调用者置 null。
 */
export interface InventoryValueRow {
    balanceId?: ReportId;
    warehouseName?: string | null;
    productName?: string | null;
    skuCode?: string | null;
    skuName?: string | null;
    quantity?: string | null;
    reservedQuantity?: string | null;
    /** 可用量 = 现有量 − 预留量，后端派生。 */
    availableQuantity?: string | null;
    /** 记账单位；一个 (仓库, SKU) 只有一个单位（Q13）。 */
    unit?: string | null;
    avgCost?: string | null;
    /** `quantity × avg_cost`，后端派生。 */
    amount?: string | null;
}

/**
 * `/inventory/value/query` 的响应：分页行 + **可选**的当前时点汇总。
 *
 * KPI 三项（账面金额 / 有货 SKU 数 / 零库存 SKU 数）是聚合值，放不进 SmartAdmin 的
 * `PageResult`，后端若不额外提供就取不到。这里按「有则显示、无则不显示」处理，
 * **不在前端把当页行的 `amount` 相加冒充总金额** —— 那会把一页当成全库。
 */
export type InventoryValuePage = ScmPage<InventoryValueRow> & InventoryValueSummary;

/** 当前库存价值的时点汇总（全部可选：后端未提供时页面不显示 KPI 行）。 */
export interface InventoryValueSummary {
    bookValue?: string | null;
    stockedSkuCount?: number | null;
    zeroStockSkuCount?: number | null;
    snapshotAt?: string | null;
}

/** 收发存（数量版）行（计划 §25）：粒度 = 仓库 + SKU + 记账单位。 */
export interface InventoryFlowSummaryRow {
    warehouseId?: ReportId;
    warehouseName?: string | null;
    skuId?: ReportId;
    skuCode?: string | null;
    skuName?: string | null;
    productName?: string | null;
    unit?: string | null;
    purchaseInQuantity?: string | null;
    salesOutQuantity?: string | null;
    stocktakeGainQuantity?: string | null;
    stocktakeLossQuantity?: string | null;
    gainReportQuantity?: string | null;
    lossReportQuantity?: string | null;
    transferInQuantity?: string | null;
    transferOutQuantity?: string | null;
    convertInQuantity?: string | null;
    convertOutQuantity?: string | null;
    /** 期内净变动量（后端按方向算好）；**没有期初 / 期末**，那需要成本快照，R0 不做。 */
    netChangeQuantity?: string | null;
}

/** `ScmInventoryReportQueryForm`：四个 Tab 共用。 */
export interface InventoryReportQuery extends ReportPage, ReportDateQuery {
    warehouseId?: ReportId;
    skuId?: ReportId;
    keyword?: string;
    movementType?: string;
    sourceDocumentType?: string;
}
