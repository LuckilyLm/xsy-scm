/**
 * W6 库存域前端类型（新增文件）。
 *
 * 字段与后端 VO / Form **逐字对齐**：
 * - VO 侧：`InventoryBalanceVO` / `InventoryMovementVO`；
 * - Form 侧：`InventoryBalanceQueryForm` / `InventoryMovementQueryForm`。
 *
 * 两条硬约束体现在类型里：
 * 1. **定点数字段一律是 `string | null`**（4 位小数）。后端用
 *    `ScmStrictDecimalStringDeserializer` 拒绝 JSON 数字，前端不得传 number；
 *    `null`（无值）与 `"0.0000"`（合法的零）是两种不同事实。
 * 2. **时间字段一律是字符串**，且后端已统一成 `yyyy-MM-dd HH:mm:ss`（北京时间）——
 *    前端直接渲染，不做 `new Date()` 二次换算（见 `common/scm-display.ts`）。
 *
 * 余额与流水的**快照 vs 实时**区别也体现在类型注释里：余额行的编码/名称是实时联表结果，
 * 流水行的 `unitSnapshot` / `unitCost` / `beforeQuantity` / `afterQuantity` 是写入时冻结的事实。
 */

export type Id = string | number;

/** 分页入参。`pageNum` / `pageSize` 必填：后端 `PageParam` 为 null 时 `convert2PageQuery` 会 NPE。 */
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
  /** Q13 记账单位：一个仓库 + SKU 只可能有一个（异单位入库会被 41001 拒绝）。 */
  unit?: string;
  quantity?: string | null;
  /** 已预留量（出库波次新增）。 */
  reservedQuantity?: string | null;
  /** 可用量 = quantity − reservedQuantity。**后端计算属性**，不落库。 */
  availableQuantity?: string | null;
  version?: number;
  updatedAt?: string;
}

/**
 * `InventoryBalanceQueryForm`。
 *
 * **没有 `sortItemList`**：后端显式拒绝客户端排序（join 查询的裸列名在四张表里都存在，
 * 交给框架拼 ORDER BY 会产生歧义列）。排序固定为 `updated_at DESC`。
 */
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

/** `InventoryMovementVO`（append-only 账本的一行）。 */
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
  /** 收货单 id（头级溯源）。 */
  sourceDocumentId?: Id;
  /** 收货行 id（防重锚点）。 */
  sourceDocumentItemId?: Id;
  /** 收货单号（Q9：人类可读来源，W6-1 不设 movement_no）。仅 `PURCHASE_IN` 有值。 */
  receiptNo?: string;
  /** 来源单号：`SALES_OUT` 取出库单号、`STOCKTAKE_*` 取盘点单号。后端 COALESCE 成一个展示列。 */
  sourceDocumentNo?: string;
  quantity?: string | null;
  unitSnapshot?: string;
  unitCost?: string | null;
  beforeQuantity?: string | null;
  afterQuantity?: string | null;
  /** 业务发生时刻 = 收货确认时刻（**不是**写入时刻）。 */
  occurredAt?: string;
  operator?: string;
  createdAt?: string;
}

/**
 * `InventoryMovementQueryForm`。
 *
 * 时间范围过滤的是 `occurred_at`（业务发生时刻），左闭右开：
 * `occurredFrom <= occurred_at < occurredTo`。
 */
export interface InventoryMovementQuery extends Page {
  warehouseId?: Id;
  skuId?: Id;
  /** SKU 编码模糊匹配（联 `product_sku`）。 */
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
// 出库单（出库波次新增）
// ------------------------------------------------------------------

/** `InventoryOutboundVO`。 */
export interface InventoryOutbound {
  id: Id;
  outboundNo?: string;
  warehouseId?: Id;
  warehouseCode?: string;
  warehouseName?: string;
  status?: string;
  /** 状态中文描述（后端按枚举填充，前端不硬编码字典）。 */
  statusDesc?: string;
  remark?: string;
  confirmedAt?: string;
  operator?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
  /** 明细；仅详情接口返回，列表为 undefined。 */
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
  /** 确认出库时写入的记账单位快照；**草稿态为空**。 */
  unitSnapshot?: string | null;
  remark?: string;
}

/**
 * `InventoryOutboundQueryForm`。
 *
 * 与余额 / 流水一致：**没有 `sortItemList`**，排序固定为 `created_at DESC, id DESC`。
 */
export interface InventoryOutboundQuery extends Page {
  outboundNo?: string;
  warehouseId?: Id;
  status?: string;
}

/**
 * `InventoryOutboundAddForm`（新建与改草稿共用）。
 *
 * 数量是**定点字符串**：后端用 `ScmStrictDecimalStringDeserializer` 拒绝 JSON 数字，
 * 前端必须传 `"1.0000"` 这样的字符串，不能传 number。
 */
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
// 库存预留（出库波次新增）
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
// 盘点单（盘点波次新增）
// ------------------------------------------------------------------

/**
 * `InventoryStocktakeVO`。
 *
 * **差异语义**（与后端一致，前端只是展示，不重算）：
 * ```
 * delta = actualQuantity - bookQuantity     // 清点发现的差异，基线是保存草稿时的账面量快照
 * after = live + delta                      // live 是确认瞬间的账面量
 * ```
 * 因此确认后的账面**不一定等于实盘数** —— 保存草稿到确认之间发生的收货 / 出库会被保留。
 * 详情页据此提示用户，避免把「账面 ≠ 实盘」误读成系统出错。
 */
export interface InventoryStocktake {
  id: Id;
  stocktakeNo?: string;
  warehouseId?: Id;
  warehouseCode?: string;
  warehouseName?: string;
  status?: string;
  /** 状态中文描述（后端按枚举填充，前端不硬编码字典）。 */
  statusDesc?: string;
  remark?: string;
  confirmedAt?: string;
  operator?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
  /** 明细；仅详情接口返回，列表为 undefined。 */
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
  /** 账面量快照（保存草稿那一刻）。 */
  bookQuantity?: string | null;
  /** 实盘量。 */
  actualQuantity?: string | null;
  /** 差异 = 实盘量 − 账面量。**后端派生字段**，不落库。 */
  deltaQuantity?: string | null;
  /** 确认盘点时写入的记账单位快照；**草稿态为空**。 */
  unitSnapshot?: string | null;
  remark?: string;
}

/**
 * `InventoryStocktakeQueryForm`。
 *
 * 与余额 / 流水 / 出库一致：**没有 `sortItemList`**，排序固定为 `created_at DESC, id DESC`。
 */
export interface InventoryStocktakeQuery extends Page {
  stocktakeNo?: string;
  warehouseId?: Id;
  status?: string;
}

/**
 * `InventoryStocktakeAddForm`（新建与改草稿共用）。
 *
 * **不提交账面量**：账面量由服务端在保存时从余额行读取并快照 ——
 * 让客户端提交账面量等于把「账」交给调用方定义，那样盘点就能凭空制造差异。
 *
 * 实盘量是**定点字符串**：后端用 `ScmStrictDecimalStringDeserializer` 拒绝 JSON 数字，
 * 前端必须传 `"12.0000"` 这样的字符串，不能传 number。允许 `"0"`（确实一件不剩），不允许负数。
 */
export interface InventoryStocktakeAdd {
  warehouseId: Id;
  remark?: string;
  items: Array<{
    skuId: Id;
    actualQuantity: string;
    remark?: string;
  }>;
}

// ------------------------------------------------------------------
// 报损报溢单（报损报溢波次新增）
// ------------------------------------------------------------------

/**
 * `InventoryLossGainVO`。
 *
 * **方向在单据头上**：`adjustType` 为 `LOSS` 时审批通过会减少库存、`OVERFLOW` 时增加库存。
 * 明细行的 `quantity` 恒为正，前端不得按正负号猜方向。
 *
 * **`version` 是审批的必填入参**：审批人必须批准自己读到的内容。若在「打开单据 → 点审批」
 * 之间单据被改过，后端会以 40921 拒绝并要求刷新 —— 因此详情/列表拿到的 `version`
 * 必须原样回传，不能在本地自增。
 */
export interface InventoryLossGain {
  id: Id;
  lossGainNo?: string;
  warehouseId?: Id;
  warehouseCode?: string;
  warehouseName?: string;
  /** `LOSS` 报损 / `OVERFLOW` 报溢。 */
  adjustType?: string;
  adjustTypeDesc?: string;
  /** `PENDING` / `COMPLETED` / `REJECTED`。 */
  status?: string;
  statusDesc?: string;
  /** 报损报溢原因（必填）。 */
  reason?: string;
  remark?: string;
  auditedAt?: string;
  auditor?: string;
  auditOpinion?: string;
  /** 乐观锁版本号；审批时必须原样回传。 */
  version?: number;
  createdAt?: string;
  updatedAt?: string;
  /** 明细；仅详情接口返回，列表为 undefined。 */
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
  /** 申报数量，恒为正；方向看单据的 `adjustType`。 */
  quantity?: string | null;
  /** 审批通过时写入的记账单位快照；**待审核态为空**。 */
  unitSnapshot?: string | null;
  remark?: string;
}

/**
 * `InventoryLossGainQueryForm`。
 *
 * 与余额 / 流水 / 出库 / 盘点一致：**没有 `sortItemList`**，排序固定为 `created_at DESC, id DESC`。
 */
export interface InventoryLossGainQuery extends Page {
  lossGainNo?: string;
  warehouseId?: Id;
  adjustType?: string;
  status?: string;
}

/**
 * `InventoryLossGainAddForm`（新建与改待审核共用）。
 *
 * `reason` 必填：报损是「把货从账上抹掉」，没有原因的单据审批人无从判断。
 * 数量是**定点字符串**：后端用 `ScmStrictDecimalStringDeserializer` 拒绝 JSON 数字。
 */
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

/**
 * `InventoryLossGainAuditForm`（审批通过 / 驳回共用）。
 *
 * `version` 必填并参与乐观锁校验；`auditOpinion` 在**驳回时必填**
 * （驳回是唯一会把「为什么不行」传达给录单人的渠道）。
 */
export interface InventoryLossGainAudit {
  version: number;
  auditOpinion?: string;
}

// ------------------------------------------------------------------
// 调拨单（调拨波次新增）
// ------------------------------------------------------------------

/**
 * `InventoryTransferVO`。
 *
 * **两步式**：`DRAFT → SHIPPED（在途）→ RECEIVED`。`SHIPPED` 期间这批货
 * **不在任何余额行里**（没有虚拟在途仓），因此全仓总库存会暂时减少 ——
 * 这是两步式的必然结果，不是缺陷。列表页必须把「在途」显示得足够醒目，
 * 否则用户会以为货丢了。
 *
 * **源仓与目标仓各带一套编码 / 名称**：调拨的语义天然是「从哪到哪」，
 * 只显示一个仓名会让用户必须点进详情才能确认方向。
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
  /** 发出时刻；草稿与已取消为空。 */
  shippedAt?: string;
  shippedBy?: string;
  /** 收货时刻；仅已完成非空。 */
  receivedAt?: string;
  receivedBy?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
  /** 明细；仅详情接口返回，列表为 undefined。 */
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
  /** 调拨数量，恒为正；方向由「发出 / 收货」动作决定。 */
  quantity?: string | null;
  /** 发出时写入的**源仓**记账单位快照；**草稿态为空**。收货时用它断言目标仓单位一致。 */
  unitSnapshot?: string | null;
  remark?: string;
}

/**
 * `InventoryTransferQueryForm`。
 *
 * 同时给出「源仓」与「目标仓」两个筛选维度：只看源仓回答「这个仓发出去了多少」，
 * 只看目标仓回答「这个仓要收多少」，两者都有查询价值。
 */
export interface InventoryTransferQuery extends Page {
  transferNo?: string;
  fromWarehouseId?: Id;
  toWarehouseId?: Id;
  status?: string;
}

/**
 * `InventoryTransferAddForm`（新建与改草稿共用）。
 *
 * 数量是**定点字符串**：后端用 `ScmStrictDecimalStringDeserializer` 拒绝 JSON 数字。
 * 源仓与目标仓必须不同（后端 41042）。
 */
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

/**
 * `InventoryInTransitVO` —— 在途库存报表的一行（按调拨单明细展开）。
 *
 * **不进 `inventory_balance`**：在途货不属于任何仓库的余额，这里以**报表**形式暴露，
 * 因此**在途量不会出现在库存余额页**。对账时必须把这份报表算进去，
 * 否则「全仓总库存」在在途期间会对不上。
 */
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
// 阈值预警（阈值预警波次新增）
// ------------------------------------------------------------------

/**
 * `InventoryWarningVO` —— 预警列表的一行（一条阈值配置 + 它对应的余额）。
 *
 * **三个数量都返回**：判定基准是**可用量**（现有量 − 预留量），只给一个数字会让用户
 * 看不懂预警为什么触发 ——「明明有 20 kg 在库，为什么说低于下限 10 kg？」
 * 的答案是那 20 kg 里有 18 kg 已预留。
 *
 * `status` 是**派生值**（`NORMAL` / `LOW` / `HIGH`），后端读时算出，不落库。
 * 配置了阈值但没有余额行时三个数量都是 `"0.0000"` —— 那正是「设了下限却一件没有」，应当预警。
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
  /** 可用量 = 现有量 − 预留量。**判定基准**。 */
  availableQuantity?: string | null;
  warnMin?: string | null;
  warnMax?: string | null;
  status?: string;
  statusDesc?: string;
}

/**
 * `InventoryWarningQueryForm`。
 *
 * **`status` 为空时的语义是「只看异常」，不是「全部」**：这是预警列表，
 * 一个全是正常项的列表对使用者没有意义。要看正常项就显式传 `NORMAL`。
 * 因此页面下拉的第一项标成「仅异常」而不是「全部」。
 */
export interface InventoryWarningQuery extends Page {
  warehouseId?: Id;
  skuId?: Id;
  skuCode?: string;
  status?: string;
}

/**
 * `InventoryWarningThresholdVO` —— 阈值配置的一行。
 *
 * 上下限各自可空：`warnMin` 为空表示不设下限，`warnMax` 为空表示不设上限。
 * 但**不能同时为空**（后端 41051）—— 都没有的配置没有任何判断依据。
 */
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
 * `InventoryWarningThresholdAddForm`（新建与编辑共用）。
 *
 * 上下限是**定点字符串**（后端拒绝 JSON 数字）；传 `undefined`/`null` 表示不设该边界。
 * 后端会校验「至少填一个、都非负、下限不高于上限」（41051）。
 */
export interface InventoryWarningThresholdAdd {
  warehouseId: Id;
  skuId: Id;
  warnMin?: string | null;
  warnMax?: string | null;
  remark?: string;
}

// ------------------------------------------------------------------
// 规格转换（规格转换波次新增）
// ------------------------------------------------------------------

/**
 * `InventoryConversionVO` —— 规格转换单。
 *
 * **跨 SKU、同仓库**：源规格 → 目标规格（如整件 → 散装）。跨仓搬运是**调拨**，不是转换。
 * 明细行上源与目标**各带一套** SKU 编码 / 名称 / 商品名：转换的语义天然是「从哪到哪」，
 * 只显示一套会让用户必须点进详情才能确认方向。
 *
 * `version` 是审批的必填入参（审批人必须批准自己读到的内容）；
 * 若期间单据被改过，后端以 40921 拒绝并要求刷新。
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
  /** 乐观锁版本号；审批时必须原样回传。 */
  version?: number;
  createdAt?: string;
  updatedAt?: string;
  /** 明细；仅详情接口返回。 */
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
  /** 源单位（**单据声明**，折算关系的一部分）。 */
  sourceUnit?: string | null;
  targetSkuId?: Id;
  targetSkuCode?: string;
  targetSkuName?: string;
  targetProductName?: string;
  /** 目标数量，恒为正；与源数量构成折算关系。 */
  targetQuantity?: string | null;
  /** 目标单位（**单据声明**）。 */
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
 * `InventoryConversionAddForm`（新建与改待审核共用）。
 *
 * 折算关系由两个数量 + 两个单位**显式声明**：后端会用两个单位分别与各自 SKU 的
 * 余额记账单位比对，不一致直接失败（41059 / 41060），**不做隐式换算**。
 * 数量是定点字符串（后端拒绝 JSON 数字）。
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
