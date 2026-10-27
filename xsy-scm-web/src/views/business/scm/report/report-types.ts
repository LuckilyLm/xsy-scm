/** 金额和数量都用定点数字符串；缺失值不当作零。 */
import type {ScmPage} from '/@/types/business/scm/customer';

/** 主键可能是数字或字符串，不参与运算。 */
export type ReportId = string | number;

/** 分页入参，两个字段都必填。 */
export interface ReportPage {
  pageNum: number;
  pageSize: number;
}

/** 非分页入参：去掉分页字段，这些接口不接收分页参数。 */
export type ReportFilterQuery<T> = Omit<T, 'pageNum' | 'pageSize'>;

/** 报表日期区间，闭区间；前端只传日期字符串。 */
export interface ReportDateQuery {
  startDate?: string;
  endDate?: string;
}

// ==================================================================
// 图表数据形状（由页面从接口数据映射）
// ==================================================================

/** TOP 条形图的一行。value 只用来画条；text 是后端原文，用于提示和标签。 */
export interface ReportChartBar {
  name: string;
  value: number | null;
  text: string;
}

/** 折线图的一条线：data 画图，texts 展示，两者等长。 */
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

/** 概览指标卡。 */
export interface ReportOverview {
  /** 区间内已确认的订单数。 */
  confirmedOrderCount?: number | null;
  /** 去重下单客户数。 */
  customerCount?: number | null;
  /** 已确认订单金额，按结算总额汇总，不按下单金额。 */
  confirmedOrderAmount?: string | null;
  /** 已完成退款额；单独展示，不冲减订单金额。 */
  completedRefundAmount?: string | null;
  submittedPurchaseOrderCount?: number | null;
  /** 已提交采购单的金额合计（含部分收货、已收货、短关）。 */
  submittedPurchaseAmount?: string | null;
  /** 采购入库成本合计。 */
  purchaseInCostAmount?: string | null;
  /** 无成本的流水行数；大于 0 说明成本合计不完整，要提示。 */
  purchaseInCostMissingCount?: number | null;
  /** 当前库存账面金额，不受日期区间影响。 */
  inventoryBookValue?: string | null;
  /** 有库存的 SKU 数。 */
  stockedSkuCount?: number | null;
  /** 账面金额的取值时点。 */
  snapshotAt?: string | null;
}

/** 财务概览的六个固定指标。 */
export interface FinanceReportOverview {
  receivableOccurredAmount: string | null;
  receivableWrittenOffAmount: string | null;
  endingReceivableAmount: string | null;
  payableOccurredAmount: string | null;
  payableWrittenOffAmount: string | null;
  endingPayableAmount: string | null;
}

export type FinanceOverviewQuery = ReportDateQuery;

export interface FinanceReportQuery extends ReportPage, ReportDateQuery {
  keyword?: string;
}

/** 应收明细行：原单、关联红字和期末余额。 */
export interface FinanceReceivableDetailRow {
  receivableId: ReportId;
  receivableNo: string;
  orderNo: string;
  customerName: string;
  amount: string;
  redAmount: string;
  netAmount: string;
  writtenOffAmount: string;
  openAmount: string;
  overAppliedAmount: string;
  eventAt: string;
}

/** 应付明细行：原单、关联红字和期末余额。 */
export interface FinancePayableDetailRow {
  payableId: ReportId;
  payableNo: string;
  purchaseOrderNo: string;
  supplierName: string;
  amount: string;
  redAmount: string;
  netAmount: string;
  writtenOffAmount: string;
  openAmount: string;
  overAppliedAmount: string;
  eventAt: string;
}

/** 按业务日汇总的一行，供趋势图和每日统计表使用。 */
export interface ReportDailyStat {
  /** 业务日期 yyyy-MM-dd；没有单据的日子也在。 */
  bizDate: string;
  confirmedOrderCount?: number | null;
  customerCount?: number | null;
  confirmedOrderAmount?: string | null;
  completedRefundAmount?: string | null;
  submittedPurchaseAmount?: string | null;
  purchaseInCostAmount?: string | null;
  purchaseInCostMissingCount?: number | null;
}

/** 概览、趋势和每日统计共用的查询条件。 */
export interface OverviewQuery extends ReportDateQuery {
  warehouseId?: ReportId;
  customerId?: ReportId;
  /** 员工 id，选择器返回 number。 */
  sellerId?: number;
  orderSource?: string;
  categoryId?: ReportId;
  keyword?: string;
}

// ==================================================================
// 销售分析
// ==================================================================

/** 通用 TOP N 行。 */
export interface SalesTopItem {
  id?: ReportId | null;
  name?: string | null;
  amount?: string | null;
}

/** 供应商采购入库成本 TOP N 行，形状同 SalesTopItem。 */
export type PurchaseTopItem = SalesTopItem;

/** 粒度：SKU × 销售单位。 */
export interface SalesProductRow {
  skuId?: ReportId;
  spuCode?: string | null;
  productName?: string | null;
  skuCode?: string | null;
  /** 下单时的规格，不实时回查商品主档。 */
  specName?: string | null;
  saleUnit?: string | null;
  rootCategoryName?: string | null;
  leafCategoryName?: string | null;
  orderCount?: number | null;
  customerCount?: number | null;
  /** 确认数量；未回写实量的行不计入。 */
  confirmedQuantity?: string | null;
  /** 成交均价 = 结算金额 / 确认数量；数量为 0 或空时为 null。 */
  avgTransactionPrice?: string | null;
  settlementAmount?: string | null;
  /** 销售金额占比（百分比），分页前计算。 */
  salesAmountSharePercent?: string | null;
  /** 按金额排序的累计占比（百分比）。 */
  cumulativeSalesAmountSharePercent?: string | null;
  /** 全局金额排名，翻页后不变。 */
  amountRank?: number | null;
}

/** 粒度：末级分类。 */
export interface SalesCategoryRow {
  categoryId?: ReportId;
  rootCategoryName?: string | null;
  leafCategoryName?: string | null;
  settlementAmount?: string | null;
  orderCount?: number | null;
  customerCount?: number | null;
  amountRank?: number | null;
}

/** 粒度：客户。 */
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

/** 粒度：销售员；没有销售员的归入「未分配销售员」。 */
export interface SalesSellerRow {
  sellerId?: ReportId | null;
  sellerName?: string | null;
  orderCount?: number | null;
  customerCount?: number | null;
  settlementAmount?: string | null;
  completedRefundAmount?: string | null;
  lastConfirmedAt?: string | null;
}

/** 粒度：订单行；业务字段都取订单行当时的值。 */
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

/**
 * 粒度：订单表头。
 * 行级看买了什么，这里看每个客户有几单、每单多少；分类和关键词只判定订单是否命中。
 */
export interface SalesOrderRow {
  orderId?: ReportId;
  orderNo?: string | null;
  confirmedAt?: string | null;
  customerCode?: string | null;
  customerName?: string | null;
  sellerName?: string | null;
  orderSource?: string | null;
  settleMode?: string | null;
  lineCount?: number | null;
  skuKindCount?: number | null;
  settlementAmount?: string | null;
  completedRefundAmount?: string | null;
}

/** 五个维度共用的查询条件。 */
export interface SalesQuery extends ReportPage, ReportDateQuery {
  advancedFilters?: import('/@/types/business/scm/advanced-filter').ScmAdvancedFilterCondition[];
  /** 仅导出使用；服务端会再校验允许的字段。 */
  exportColumnKeys?: string[];
  customerId?: ReportId;
  /** 员工 id。 */
  sellerId?: number;
  orderSource?: string;
  categoryId?: ReportId;
  keyword?: string;
}

// ==================================================================
// 采购分析
// ==================================================================

/** 采购概览指标卡。 */
export interface PurchaseOverview {
  submittedOrderCount?: number | null;
  submittedAmount?: string | null;
  confirmedReceiptCount?: number | null;
  /** 收货数量 × 采购单价，只用于核对价格和数量，不是应付金额。 */
  receiptReferenceAmount?: string | null;
  purchaseInCostAmount?: string | null;
  purchaseInCostMissingCount?: number | null;
  /** 待入库的收货单数（仓库确认、已确认、待处理）。 */
  pendingPutawayReceiptCount?: number | null;
}

/** 粒度：SKU × 采购单位。 */
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
  /** 采购均价 = 行金额合计 / 计划数量合计，不是单价的算术平均。 */
  avgPurchasePrice?: string | null;
  /** 入库数量按库存单位分组，用文本展示。 */
  inboundQuantityText?: string | null;
  inboundCostAmount?: string | null;
  inboundCostMissingCount?: number | null;
}

/** 粒度：供应商。 */
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

/** 粒度：采购员。 */
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

/** 粒度：采购行。 */
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

/** 粒度：业务日 × SKU × 采购单位。 */
export interface PurchasePriceTrendPoint {
  bizDate?: string | null;
  skuId?: ReportId;
  skuCode?: string | null;
  productName?: string | null;
  /** 不同单位不合并成一条线。 */
  purchaseUnit?: string | null;
  weightedAvgPrice?: string | null;
  /** 上一次有效均价，只在相同规格和单位间比较。 */
  previousWeightedAvgPrice?: string | null;
  priceChangeAmount?: string | null;
  /** 百分比；基期为 0 时为 null。 */
  priceChangePercent?: string | null;
  sampleLineCount?: number | null;
}

/** 六个维度共用的查询条件。 */
export interface PurchaseQuery extends ReportPage, ReportDateQuery {
  advancedFilters?: import('/@/types/business/scm/advanced-filter').ScmAdvancedFilterCondition[];
  /** 仅导出使用；成本列由后端权限控制。 */
  exportColumnKeys?: string[];
  supplierId?: ReportId;
  /** 员工 id。 */
  purchaserId?: number;
  warehouseId?: ReportId;
  status?: string;
  keyword?: string;
  /** 价格波动维度按 SKU 收窄，避免不同单位画进同一线。 */
  skuId?: ReportId;
}

// ==================================================================
// 收货与入库
// ==================================================================

export interface ReceiptUnitQuantity {
  unit: string | null;
  quantity: string | null;
}

export interface ReceiptSummary {
  receiptCount: number;
  itemCount: number;
  quantities: ReceiptUnitQuantity[];
  referenceAmount: string | null;
}

export interface InboundSummary {
  movementCount: number;
  quantities: ReceiptUnitQuantity[];
  /** 只含已知成本流水的小计，不是全量成本。 */
  knownCostAmount: string | null;
  costMissingCount: number | null;
}

export interface PendingPutawaySummary {
  receiptCount: number;
  quantities: ReceiptUnitQuantity[];
}

/** 粒度：收货明细行。 */
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
  /** 入库状态；已确认收货不等于已入库。 */
  putawayStatus?: string | null;
  confirmedAt?: string | null;
  confirmedBy?: string | null;
  spuCode?: string | null;
  productName?: string | null;
  skuCode?: string | null;
  skuName?: string | null;
  purchaseUnit?: string | null;
  receivedQuantity?: string | null;
  /** 该采购行的累计已收量，不要按行相加。 */
  cumulativeReceivedQuantity?: string | null;
  remainingQuantity?: string | null;
  overReceiptQuantity?: string | null;
  receiptDifference?: string | null;
  purchasePrice?: string | null;
  /** 本次收货量 × 采购单价，称为收货参考金额。 */
  receiptReferenceAmount?: string | null;
}

/** 粒度：一条采购入库流水。 */
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
  /** 成本缺失或没有成本权限时为 true：显示 — 而不是 0。 */
  costMissing?: boolean | null;
  operator?: string | null;
}

/** 粒度：一张待入库收货单。 */
export interface PendingPutawayRow {
  receiptId?: ReportId;
  receiptNo?: string | null;
  purchaseOrderNo?: string | null;
  purchaseOrderId?: ReportId;
  supplierName?: string | null;
  warehouseName?: string | null;
  confirmedAt?: string | null;
  skuKindCount?: number | null;
  /** 按单位分组的数量文本（如 12kg / 3箱）。 */
  quantityText?: string | null;
}

/** 三个标签页共用的查询条件。 */
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

/** 库存流水报表行。方向由 movementType 推出，不在字段里。 */
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
  /** 来源单号（收货单、出库单、盘点单等）。 */
  sourceDocumentNo?: string | null;
  sourceDocumentId?: ReportId | null;
  sourceDocumentItemId?: ReportId | null;
  quantity?: string | null;
  /** 记账时的单位。 */
  unitSnapshot?: string | null;
  unitCost?: string | null;
  /** 成本金额，后端算；无成本或无权限时为 null。 */
  costAmount?: string | null;
  beforeQuantity?: string | null;
  afterQuantity?: string | null;
  operator?: string | null;
}

/** 损耗分析指标。数量按单位分组，只能看文本。 */
export interface InventoryLossSummary {
  /** 盘亏数量文本（按记账单位分组）。 */
  stocktakeLossQuantityText?: string | null;
  stocktakeLossCostAmount?: string | null;
  /** 报损数量文本（按记账单位分组）。 */
  lossReportQuantityText?: string | null;
  lossReportCostAmount?: string | null;
  /** 损耗总成本，后端聚合。 */
  totalLossCostAmount?: string | null;
  /** 无成本行数；大于 0 时相关金额为 null，不用部分数据冒充全额。 */
  costMissingCount?: number | null;
  /** 按日损耗成本趋势；后端没给就显示空图，不拿一页明细自己拼。 */
  dailyTrend?: InventoryLossTrendPoint[] | null;
}

/** 损耗按日趋势点。 */
export interface InventoryLossTrendPoint {
  bizDate?: string | null;
  costMissingCount?: number | null;
  stocktakeLossCostAmount?: string | null;
  lossReportCostAmount?: string | null;
  totalLossCostAmount?: string | null;
}

/** 损耗明细行（表列）。 */
export interface InventoryLossRow {
  movementId?: ReportId;
  productName?: string | null;
  skuCode?: string | null;
  warehouseName?: string | null;
  /** 损耗类型，和库存流水的 movementType 不是一回事。 */
  lossType?: string | null;
  quantity?: string | null;
  unit?: string | null;
  unitCost?: string | null;
  costAmount?: string | null;
  sourceDocumentNo?: string | null;
  occurredAt?: string | null;
  operator?: string | null;
}

/**
 * 当前库存价值行。
 * 当前时点值，不受日期区间影响；成本列受权限控制，无权限时后端置 null。
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
  /** 记账单位；一个 (仓库, SKU) 只有一个单位。 */
  unit?: string | null;
  avgCost?: string | null;
  /** 库存金额 = 数量 × 平均成本。 */
  amount?: string | null;
}

/** 库存价值查询结果：分页行 + 可选汇总。汇总缺失时不显示指标卡，不用当页数据替代。 */
export type InventoryValuePage = ScmPage<InventoryValueRow> & InventoryValueSummary;

/** 库存价值汇总；字段都可选。 */
export interface InventoryValueSummary {
  bookValue?: string | null;
  stockedSkuCount?: number | null;
  zeroStockSkuCount?: number | null;
  snapshotAt?: string | null;
}

/** 收发存（数量）行：粒度 = 仓库 + SKU + 单位。 */
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
  /** 期内净变动量。 */
  netChangeQuantity?: string | null;
}

/** 四个标签页共用的查询条件。 */
export interface InventoryReportQuery extends ReportPage, ReportDateQuery {
  warehouseId?: ReportId;
  skuId?: ReportId;
  keyword?: string;
  movementType?: string;
  sourceDocumentType?: string;
}

/**
 * 负库存损耗行：查询时点的余额快照（负数量），不是历史月份回算。
 * 负库存只说明「卖出去了但没入成本」，真实损耗仍以报损/盘亏为准。
 */
export interface InventoryNegativeStockRow {
  balanceId?: ReportId;
  warehouseId?: ReportId;
  warehouseName?: string | null;
  skuId?: ReportId;
  productName?: string | null;
  skuCode?: string | null;
  specName?: string | null;
  /** 余额记账单位；一个 (仓库, 规格) 只有一个单位。 */
  unit?: string | null;
  /** 负数。 */
  quantity?: string | null;
  /** 余额均价；无成本权限时为 null。 */
  avgCost?: string | null;
  /** 负数量 × 平均成本，符号为负；无成本权限时为 null。 */
  estimatedAmount?: string | null;
  /** 最近一条销售出库流水的时间；从未销售出库时为 null。 */
  lastOutboundAt?: string | null;
  lastOutboundNo?: string | null;
}

/** 负库存损耗指标；数量跨单位不可相加，只统计规格数、仓库数与金额。 */
export interface InventoryNegativeStockSummary {
  rowCount?: number | null;
  warehouseCount?: number | null;
  /** 均价为 0 的行数：成本未知，金额会偏低，页面必须提示。 */
  zeroCostRowCount?: number | null;
  totalEstimatedAmount?: string | null;
  /** 取数时点。 */
  snapshotAt?: string | null;
}

/** 负库存查询没有日期：余额是当前时点状态，按日期筛选会得到一张假装是历史的表。 */
export interface InventoryNegativeStockQuery extends ReportPage {
  warehouseId?: ReportId;
  skuId?: ReportId;
  keyword?: string;
}
