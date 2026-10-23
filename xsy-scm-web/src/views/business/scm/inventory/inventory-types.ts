/**
 * 库存模块的前端类型，与后端接口保持一致。
 * 数量和金额用定点字符串：null 表示无值，"0.0000" 表示零。
 * 时间按后端返回的北京时间显示，不再转换时区。
 */

export type Id = string | number;

/** 分页入参。`pageNum` / `pageSize` 必填，否则后端会报空指针。 */
export interface Page {
  pageNum: number;
  pageSize: number;
}

// ------------------------------------------------------------------
// 库存余额
// ------------------------------------------------------------------

/** `InventoryBalanceVO`（粒度 = warehouse + sku）。 */
export interface InventoryBalance {
  id: Id;
  warehouseId?: Id;
  warehouseCode?: string;
  warehouseName?: string;
  skuId?: Id;
  skuCode?: string;
  /** SKU 名称（来自 `product_sku.spec_name`）。 */
  skuName?: string;
  /** 商品名称（来自 `product_spu.name`）。 */
  productName?: string;
  specValues?: Record<string, unknown> | null;
  /** 记账单位；同一仓库和 SKU 只有一个，单位不同会被 41001 拒绝。 */
  unit?: string;
  quantity?: string | null;
  /** 已预留量。 */
  reservedQuantity?: string | null;
  /** 可用量 = 数量 − 预留量，由后端计算。 */
  availableQuantity?: string | null;
  /** 移动加权平均成本。没有采购入库记录时初始为 0，首次上线可能是估算值。 */
  avgCost?: string;
  /** 库存金额 = 数量 × 成本，由后端计算。 */
  amount?: string | null;
  version?: number;
  updatedAt?: string;
}

/** 不支持客户端排序，固定按更新时间倒序（联表排序会出现重名列）。 */
export interface InventoryBalanceQuery extends Page {
  warehouseId?: Id;
  skuId?: Id;
  /** SKU 编码模糊匹配。 */
  skuCode?: string;
  /** 商品名称模糊匹配。 */
  productName?: string;
}

// ------------------------------------------------------------------
// 库存流水
// ------------------------------------------------------------------

/** `InventoryMovementVO`（只增不改的库存流水）。 */
export interface InventoryMovement {
  id: Id;
  warehouseId?: Id;
  warehouseCode?: string;
  warehouseName?: string;
  skuId?: Id;
  skuCode?: string;
  skuName?: string;
  productName?: string;
  specValues?: Record<string, unknown> | null;
  movementType?: string;
  sourceDocumentType?: string;
  /** 收货单 ID。 */
  sourceDocumentId?: Id;
  /** 收货行 ID，用于防重。 */
  sourceDocumentItemId?: Id;
  /** 收货单号，仅 `PURCHASE_IN` 有值。 */
  receiptNo?: string;
  /** 来源单号：销售出库取出库单号，盘点取盘点单号。 */
  sourceDocumentNo?: string;
  quantity?: string | null;
  unitSnapshot?: string;
  unitCost?: string | null;
  beforeQuantity?: string | null;
  afterQuantity?: string | null;
  /** 业务发生时间（收货确认时间），不是写入时间。 */
  occurredAt?: string;
  operator?: string;
  createdAt?: string;
}

/** 按业务发生时间筛选，左闭右开：`occurredFrom <= 时间 < occurredTo`。 */
export interface InventoryMovementQuery extends Page {
  warehouseId?: Id;
  skuId?: Id;
  /** SKU 编码，模糊匹配。 */
  skuCode?: string;
  /** 流水类型：`PURCHASE_IN` / `SALES_OUT` / `STOCKTAKE_GAIN` / `STOCKTAKE_LOSS` / `LOSS_REPORT` / `GAIN_REPORT`。 */
  movementType?: string;
  /** 来源单据类型：收货行 / 出库单行 / 销售订单行 / 盘点单行 / 报损报溢单行。 */
  sourceDocumentType?: string;
  sourceDocumentId?: Id;
  occurredFrom?: string | null;
  occurredTo?: string | null;
}

// ------------------------------------------------------------------
// 出库单
// ------------------------------------------------------------------

/** `InventoryOutboundVO`。 */
export interface InventoryOutbound {
  id: Id;
  outboundNo?: string;
  warehouseId?: Id;
  warehouseCode?: string;
  warehouseName?: string;
  status?: string;
  /** 状态名称，由后端返回。 */
  statusDesc?: string;
  remark?: string;
  confirmedAt?: string;
  operator?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
  /** 明细，仅详情接口返回。 */
  items?: InventoryOutboundItem[];
}

/** `InventoryOutboundVO.Item`。 */
export interface InventoryOutboundItem {
  id?: Id;
  skuId?: Id;
  skuCode?: string;
  skuName?: string;
  productName?: string;
  specValues?: Record<string, unknown> | null;
  quantity?: string | null;
  /** 确认出库时的记账单位，草稿为空。 */
  unitSnapshot?: string | null;
  remark?: string;
}

/** 不支持客户端排序，固定按创建时间和 ID 倒序。 */
export interface InventoryOutboundQuery extends Page {
  outboundNo?: string;
  warehouseId?: Id;
  status?: string;
}

/** 新建和修改草稿共用。数量必须传定点字符串（如 `"1.0000"`），后端不接受 JSON 数字。 */
export interface InventoryOutboundAdd {
  warehouseId: Id;
  remark?: string;
  items: Array<{
    skuId: Id;
    quantity: string;
    remark?: string;
  }>;
}

// ------------------------------------------------------------------
// 库存预留
// ------------------------------------------------------------------

/** `InventoryReservationVO`。 */
export interface InventoryReservation {
  id: Id;
  warehouseId?: Id;
  warehouseCode?: string;
  warehouseName?: string;
  skuId?: Id;
  skuCode?: string;
  skuName?: string;
  specValues?: Record<string, string> | null;
  productName?: string;
  sourceDocumentType?: string;
  sourceDocumentId?: Id;
  sourceDocumentItemId?: Id;
  /** 来源单号（联销售订单取，可能为空）。 */
  sourceDocumentNo?: string;
  quantity?: string | null;
  unitSnapshot?: string;
  status?: string;
  statusDesc?: string;
  occurredAt?: string;
  operator?: string;
  createdAt?: string;
}

/** `InventoryReservationQueryForm`。 */
export interface InventoryReservationQuery extends Page {
  warehouseId?: Id;
  skuId?: Id;
  status?: string;
  sourceDocumentId?: Id;
}

// ------------------------------------------------------------------
// 盘点单
// ------------------------------------------------------------------

/**
 * 盘点差异 = 实盘量 − 保存草稿时的账面量。
 * 确认时把差异加到最新账面量上，所以确认后的库存不一定等于实盘量。
 * 前端只展示后端结果，不重新计算。
 */
export interface InventoryStocktake {
  id: Id;
  stocktakeNo?: string;
  warehouseId?: Id;
  warehouseCode?: string;
  warehouseName?: string;
  status?: string;
  /** 状态名称，由后端返回。 */
  statusDesc?: string;
  remark?: string;
  confirmedAt?: string;
  operator?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
  /** 明细，仅详情接口返回。 */
  items?: InventoryStocktakeItem[];
}

/** `InventoryStocktakeVO.Item`。 */
export interface InventoryStocktakeItem {
  id?: Id;
  skuId?: Id;
  skuCode?: string;
  skuName?: string;
  productName?: string;
  specValues?: Record<string, unknown> | null;
  /** 保存草稿时的账面量。 */
  bookQuantity?: string | null;
  /** 实盘量。 */
  actualQuantity?: string | null;
  /** 差异 = 实盘量 − 账面量，由后端计算。 */
  deltaQuantity?: string | null;
  /** 确认盘点时的记账单位，草稿为空。 */
  unitSnapshot?: string | null;
  remark?: string;
}

/** 不支持客户端排序，固定按创建时间和 ID 倒序。 */
export interface InventoryStocktakeQuery extends Page {
  stocktakeNo?: string;
  warehouseId?: Id;
  status?: string;
}

/** 新建或修改盘点草稿只提交实盘量，账面量由后端读取；实盘量必须是非负定点字符串。 */
export interface InventoryStocktakeAdd {
  warehouseId: Id;
  remark?: string;
  items: Array<{
    skuId: Id;
    actualQuantity: string;
    remark?: string;
  }>;
}

/** 盘点导入的单行错误。整批校验失败时不会创建盘点单，`stocktakeId` 为空。 */
export interface StocktakeImportError {
  row?: number;
  skuCode?: string;
  column?: string;
  code: string;
  message: string;
}

/** 盘点 Excel 导入结果（后端 `InventoryStocktakeImportResultVO`）。成功时 `stocktakeId` 为新 DRAFT。 */
export interface StocktakeImportResult {
  totalRows: number;
  totalErrors: number;
  stocktakeId?: Id | null;
  importedItems: number;
  replayed: boolean;
  errors: StocktakeImportError[];
}

// ------------------------------------------------------------------
// 报损报溢单
// ------------------------------------------------------------------

/**
 * `InventoryLossGainVO`。
 *
 * `adjustType` 为 `LOSS` 时审批后减库存，`OVERFLOW` 时加库存；
 * 明细数量恒为正，方向不要看正负号。
 * `version` 必须原样回传；单据被改过时后端会以 40921 拒绝。
 */
export interface InventoryLossGain {
  id: Id;
  lossGainNo?: string;
  warehouseId?: Id;
  warehouseCode?: string;
  warehouseName?: string;
  /** `LOSS` 报损，`OVERFLOW` 报溢。 */
  adjustType?: string;
  adjustTypeDesc?: string;
  /** `PENDING` / `COMPLETED` / `REJECTED`。 */
  status?: string;
  statusDesc?: string;
  /** 报损报溢原因，必填。 */
  reason?: string;
  remark?: string;
  auditedAt?: string;
  auditor?: string;
  auditOpinion?: string;
  /** 乐观锁版本号，审批时必须原样回传。 */
  version?: number;
  createdAt?: string;
  updatedAt?: string;
  /** 明细，仅详情接口返回。 */
  items?: InventoryLossGainItem[];
}

/** `InventoryLossGainVO.Item`。 */
export interface InventoryLossGainItem {
  id?: Id;
  skuId?: Id;
  skuCode?: string;
  skuName?: string;
  productName?: string;
  specValues?: Record<string, unknown> | null;
  /** 申报数量，恒为正；方向看单头的 `adjustType`。 */
  quantity?: string | null;
  /** 审批通过时的记账单位，待审核为空。 */
  unitSnapshot?: string | null;
  remark?: string;
}

/** 不支持客户端排序，固定按创建时间和 ID 倒序。 */
export interface InventoryLossGainQuery extends Page {
  lossGainNo?: string;
  warehouseId?: Id;
  adjustType?: string;
  status?: string;
}

/** 新建和修改待审核单据共用。原因必填；数量必须是定点字符串。 */
export interface InventoryLossGainAdd {
  adjustType: string;
  warehouseId: Id;
  reason: string;
  remark?: string;
  items: Array<{
    skuId: Id;
    quantity: string;
    remark?: string;
  }>;
}

/** 审批通过和驳回共用。`version` 必填；驳回时必须填写意见。 */
export interface InventoryLossGainAudit {
  version: number;
  auditOpinion?: string;
}

// ------------------------------------------------------------------
// 调拨单
// ------------------------------------------------------------------

/**
 * `InventoryTransferVO`。
 *
 * 流程为 DRAFT → SHIPPED（在途）→ RECEIVED。在途货物不在任何仓库余额中，
 * 全仓总库存会暂时减少，列表要醒目显示“在途”。
 * 源仓和目标仓各带编码、名称，方便直接看清方向。
 */
export interface InventoryTransfer {
  id: Id;
  transferNo?: string;
  fromWarehouseId?: Id;
  fromWarehouseCode?: string;
  fromWarehouseName?: string;
  toWarehouseId?: Id;
  toWarehouseCode?: string;
  toWarehouseName?: string;
  /** `DRAFT` / `SHIPPED`（在途）/ `RECEIVED` / `CANCELLED`。 */
  status?: string;
  statusDesc?: string;
  remark?: string;
  /** 发出时间；草稿和已取消为空。 */
  shippedAt?: string;
  shippedBy?: string;
  /** 收货时间；仅已完成有值。 */
  receivedAt?: string;
  receivedBy?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
  /** 明细，仅详情接口返回。 */
  items?: InventoryTransferItem[];
}

/** `InventoryTransferVO.Item`。 */
export interface InventoryTransferItem {
  id?: Id;
  skuId?: Id;
  skuCode?: string;
  skuName?: string;
  productName?: string;
  specValues?: Record<string, unknown> | null;
  /** 调拨数量，恒为正；方向由“发出 / 收货”动作决定。 */
  quantity?: string | null;
  /** 发出时的源仓记账单位，收货时用于校验目标仓单位一致。 */
  unitSnapshot?: string | null;
  remark?: string;
}

/** 可按源仓或目标仓筛选，分别查看发出量和待收货量。 */
export interface InventoryTransferQuery extends Page {
  transferNo?: string;
  fromWarehouseId?: Id;
  toWarehouseId?: Id;
  status?: string;
}

/** 新建和修改草稿共用。数量必须是定点字符串；源仓和目标仓不能相同。 */
export interface InventoryTransferAdd {
  fromWarehouseId: Id;
  toWarehouseId: Id;
  remark?: string;
  items: Array<{
    skuId: Id;
    quantity: string;
    remark?: string;
  }>;
}

/** 在途库存报表的一行（按调拨单明细展开）。在途货不在库存余额中，对账时要一起计算。 */
export interface InventoryInTransit {
  transferNo?: string;
  fromWarehouseId?: Id;
  fromWarehouseName?: string;
  toWarehouseId?: Id;
  toWarehouseName?: string;
  skuId?: Id;
  skuCode?: string;
  skuName?: string;
  quantity?: string | null;
  unit?: string | null;
}

// ------------------------------------------------------------------
// 阈值预警
// ------------------------------------------------------------------

/** 一次预警检查的结果。检查数和发送数都要显示：发送 0 条可能是没有异常，也可能是没有配置阈值。 */
export interface InventoryWarningScanResult {
  /** 本轮检查的阈值配置条数。 */
  scannedCount: number;
  /** 本轮发送的站内信条数；同一预警发给多人会各计一条。 */
  sentCount: number;
}

/**
 * 预警列表的一行（一条阈值配置和对应余额）。
 *
 * 用可用量（现有量 − 预留量）判断，所以同时返回三个数量，便于解释为什么触发。
 * `status` 为 `NORMAL` / `LOW` / `HIGH`，由后端计算。
 * 配置了阈值但没有余额时三个数量都是 `"0.0000"`，此时也应预警。
 */
export interface InventoryWarning {
  /** 阈值配置 id（可跳转到配置页）。 */
  thresholdId?: Id;
  warehouseId?: Id;
  warehouseCode?: string;
  warehouseName?: string;
  skuId?: Id;
  skuCode?: string;
  skuName?: string;
  productName?: string;
  specValues?: Record<string, unknown> | null;
  /** 记账单位；没有余额行时为空。 */
  unit?: string | null;
  quantity?: string | null;
  reservedQuantity?: string | null;
  /** 可用量 = 现有量 − 预留量。判定基准。 */
  availableQuantity?: string | null;
  warnMin?: string | null;
  warnMax?: string | null;
  status?: string;
  statusDesc?: string;
}

/** `status` 为空时只查异常项；要看正常项需显式传 `NORMAL`。 */
export interface InventoryWarningQuery extends Page {
  warehouseId?: Id;
  skuId?: Id;
  skuCode?: string;
  /** 阈值配置 ID，用于站内信跳转到对应预警。 */
  thresholdId?: Id;
  status?: string;
}

/** 阈值配置的一行。上下限可以只设一个，但不能都为空（41051）。 */
export interface InventoryWarningThreshold {
  id: Id;
  warehouseId?: Id;
  warehouseCode?: string;
  warehouseName?: string;
  skuId?: Id;
  skuCode?: string;
  skuName?: string;
  productName?: string;
  specValues?: Record<string, unknown> | null;
  warnMin?: string | null;
  warnMax?: string | null;
  remark?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

/** `InventoryWarningThresholdQueryForm`。 */
export interface InventoryWarningThresholdQuery extends Page {
  warehouseId?: Id;
  skuId?: Id;
  skuCode?: string;
}

/**
 * 新建和编辑共用。上下限是定点字符串，null 表示不设该边界；
 * 后端要求至少填一个、不能为负、下限不高于上限（41051）。
 */
export interface InventoryWarningThresholdAdd {
  warehouseId: Id;
  skuId: Id;
  warnMin?: string | null;
  warnMax?: string | null;
  remark?: string;
}

// ------------------------------------------------------------------
// 规格转换
// ------------------------------------------------------------------

/**
 * `InventoryConversionVO` —— 规格转换单。
 *
 * 同一仓库内源规格转换成目标规格（如整件拆零），跨仓搬运属于调拨。
 * 明细行带源和目标两套 SKU 信息，方便直接看清方向。
 * `version` 审批时必须原样回传；单据被改过会以 40921 拒绝。
 */
export interface InventoryConversion {
  id: Id;
  conversionNo?: string;
  warehouseId?: Id;
  warehouseCode?: string;
  warehouseName?: string;
  /** `SPLIT` 整件拆零 / `COMBINE` 组合拆分。 */
  convertType?: string;
  convertTypeDesc?: string;
  /** `PENDING` / `COMPLETED` / `REJECTED`。 */
  status?: string;
  statusDesc?: string;
  reason?: string;
  remark?: string;
  auditedAt?: string;
  auditor?: string;
  auditOpinion?: string;
  /** 乐观锁版本号，审批时必须原样回传。 */
  version?: number;
  createdAt?: string;
  updatedAt?: string;
  /** 明细，仅详情接口返回。 */
  items?: InventoryConversionItem[];
}

/** `InventoryConversionVO.Item`。 */
export interface InventoryConversionItem {
  id?: Id;
  sourceSkuId?: Id;
  sourceSkuCode?: string;
  sourceSkuName?: string;
  sourceProductName?: string;
  /** 源数量，恒为正。 */
  sourceQuantity?: string | null;
  /** 源单位。 */
  sourceUnit?: string | null;
  targetSkuId?: Id;
  targetSkuCode?: string;
  targetSkuName?: string;
  targetProductName?: string;
  /** 目标数量，恒为正，与源数量构成折算关系。 */
  targetQuantity?: string | null;
  /** 目标单位。 */
  targetUnit?: string | null;
  remark?: string;
}

/** `InventoryConversionQueryForm`。 */
export interface InventoryConversionQuery extends Page {
  conversionNo?: string;
  warehouseId?: Id;
  convertType?: string;
  status?: string;
}

/**
 * 新建和修改待审核单据共用。
 * 源/目标数量与单位必须与各自 SKU 的记账单位一致，后端不做自动换算（41059 / 41060）。
 */
export interface InventoryConversionAdd {
  warehouseId: Id;
  convertType: string;
  reason?: string;
  remark?: string;
  items: Array<{
    sourceSkuId: Id;
    sourceQuantity: string;
    sourceUnit: string;
    targetSkuId: Id;
    targetQuantity: string;
    targetUnit: string;
    remark?: string;
  }>;
}

/** `InventoryConversionAuditForm`（审批 / 驳回共用）。 */
export interface InventoryConversionAudit {
  version: number;
  auditOpinion?: string;
}

// ------------------------------------------------------------------
// 批次库存（一期：L1 批次，不含包装码）
// ------------------------------------------------------------------

/** 与余额查询一致：不传仓库时只返回有权限的仓库，不会因此扩大范围。 */
export interface InventoryLotBalanceQuery extends Page {
  warehouseId?: Id;
  skuId?: Id;
  lotNo?: string;
  sourceLotNo?: string;
  productName?: string;
}

/** `InventoryLotBalanceVO`（粒度 = warehouse + sku + lot）。 */
export interface InventoryLotBalance {
  id: Id;
  warehouseId: Id;
  warehouseCode: string;
  warehouseName: string;
  skuId: Id;
  skuCode: string;
  skuName: string;
  productName: string;
  lotId: Id;
  /** 内部批次号，系统生成，只读。 */
  lotNo: string;
  /** 供应商或现场提供的生产批号，可能为空。 */
  sourceLotNo?: string | null;
  unit: string;
  quantity: string;
  updatedAt: string;
}

/** `InventoryLotMovementQueryForm`。 */
export interface InventoryLotMovementQuery extends Page {
  warehouseId?: Id;
  skuId?: Id;
  lotNo?: string;
  movementType?: string;
}

/** `InventoryLotMovementVO`：`quantity` 有符号，入库为正、出库为负。 */
export interface InventoryLotMovement {
  id: Id;
  warehouseId: Id;
  warehouseName: string;
  skuId: Id;
  skuCode: string;
  skuName: string;
  lotId: Id;
  lotNo: string;
  movementType: string;
  sourceDocumentType: string;
  sourceDocumentId: Id;
  sourceDocumentItemId?: Id | null;
  quantity: string;
  unitSnapshot: string;
  beforeQuantity: string;
  afterQuantity: string;
  occurredAt: string;
  operator: string;
}

// ------------------------------------------------------------------
// 包装码（一物一码，二期）
// ------------------------------------------------------------------

/** `TraceUnitQueryForm`。 */
export interface TraceUnitQuery extends Page {
  lotId?: Id;
  unitCode?: string;
  lotNo?: string;
  status?: string;
}

/** `TraceUnitVO.Unit`：一个包装码对应一个批次，码本身不含业务信息。 */
export interface TraceUnit {
  id: Id;
  unitCode: string;
  /** 二维码中的公开凭据，用于拼扫码链接。 */
  publicToken?: string;
  lotId: Id;
  lotNo: string;
  sourceLotNo?: string | null;
  packageType: string;
  quantity: string;
  unit: string;
  /** 净重；未称重时为空。 */
  netWeight?: string | null;
  weightUnit?: string | null;
  /** SCALE 电子秤 / MANUAL 后台补录。 */
  weightSource?: string | null;
  measuredAt?: string | null;
  status: string;
  remark?: string | null;
  createdAt: string;
  createdBy: string;
}

/** `TraceUnitIssueForm`：一次给同一批次发 N 个同规格包装。 */
export interface TraceUnitIssue {
  lotId: Id;
  packageType: string;
  packageCount: number;
  quantityPerPackage: string;
  remark?: string | null;
}

/** `TraceUnitVO.Binding`：分拣时包装码绑定到哪条任务行和客户。 */
export interface TraceUnitBinding {
  sortingTaskId: Id;
  sortingTaskItemId: Id;
  orderNo?: string | null;
  customerName?: string | null;
  boundAt?: string | null;
  boundBy?: string | null;
}

/** `TraceUnitVO.Replay`：一个包装码的完整流转记录。 */
export interface TraceUnitReplay {
  unit: TraceUnit;
  lot: {lotId: Id; lotNo: string; sourceLotNo?: string | null; skuCode?: string; skuName?: string};
  receipt?: {receiptNo: string; supplierName?: string | null; confirmedAt?: string | null} | null;
  binding?: TraceUnitBinding | null;
  outbounds: {outboundNo: string; occurredAt: string; quantity: string}[];
}

// ------------------------------------------------------------------
// 包装码更正（解绑需他人审批）
// ------------------------------------------------------------------

/** `TraceCorrectionQueryForm`。 */
export interface TraceCorrectionQuery extends Page {
  status?: string;
  unitCode?: string;
  lotNo?: string;
}

/** `TraceCorrectionVO`：一条申请对应一条绑定。 */
export interface TraceCorrection {
  id: Id;
  unitId: Id;
  unitCode: string;
  lotId: Id;
  lotNo?: string | null;
  bindingId: Id;
  customerName?: string | null;
  orderNo?: string | null;
  reason: string;
  status: string;
  requestedBy: string;
  requestedAt: string;
  decidedBy?: string | null;
  decidedAt?: string | null;
  decisionReason?: string | null;
  version: number;
}

/** 审批载荷。 */
export interface TraceCorrectionDecision {
  id: Id;
  version: number;
  reason?: string | null;
}
