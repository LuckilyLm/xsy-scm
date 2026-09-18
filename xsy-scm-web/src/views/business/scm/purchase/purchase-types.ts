/**
 * W5 采购域前端类型（新增文件，无 Provenance 要求）。
 *
 * 字段与后端 VO / Form **逐字对齐**：
 * - VO 侧：`PurchaseOrderVO` / `PurchaseOrderItemVO` / `PurchaseOrderAllocationVO` /
 *   `PurchaseDemandVO` / `PurchaseReceiptVO` / `PurchaseReceiptItemVO` / `PurchaseOperationLogVO`；
 * - Form 侧：`PurchaseOrderAddForm` / `PurchaseOrderUpdateForm` / `PurchaseReceiptConfirmForm` 等。
 *
 * 两条硬约束体现在类型里：
 * 1. **定点数字段一律是 `string | null`**（4 位小数）。后端用
 *    `ScmStrictDecimalStringDeserializer` 拒绝 JSON 数字，前端不得传 number；
 *    `null`（未定价 / 未分配）与 `"0.0000"`（合法的零）是两种不同事实。
 * 2. **`version` 必填且为 `number`**，乐观锁靠它。
 */

export type Id = string | number;

/** 分页入参。`pageNum` / `pageSize` 必填：后端 `PageParam` 为 null 时 `convert2PageQuery` 会 NPE。 */
export interface Page {
  pageNum: number;
  pageSize: number;
}

// ------------------------------------------------------------------
// 采购需求
// ------------------------------------------------------------------

/** `PurchaseDemandVO`。 */
export interface Demand {
  id: Id;
  salesOrderId?: Id;
  salesOrderNoSnapshot?: string;
  salesOrderItemId?: Id;
  skuId?: Id;
  skuCode?: string;
  skuName?: string;
  productName?: string;
  specValues?: Record<string, unknown> | null;
  demandUnit?: string;
  productType?: string;
  requiredQuantity?: string | null;
  allocatedQuantity?: string | null;
  unallocatedQuantity?: string | null;
  supplierId?: Id | null;
  supplierName?: string | null;
  warehouseId?: Id | null;
  warehouseName?: string | null;
  status?: string;
  demandDate?: string;
  sourceConfirmedAt?: string;
  version?: number;
  createdAt?: string;
}

export interface DemandQuery extends Page {
  salesOrderNo?: string;
  skuId?: Id;
  supplierId?: Id;
  warehouseId?: Id;
  status?: string;
  demandDateFrom?: string;
  demandDateTo?: string;
}

/** `PurchaseDemandGenerateForm` —— 半开区间 `[startAt, endAt)`（Q6a）。 */
export interface DemandGenerate {
  startAt: string;
  endAt: string;
  warehouseId: Id;
  supplierId?: Id | null;
  purchaserId?: Id | null;
}

/** `PurchaseDemandService.GenerateResult`。 */
export interface GenerateResult {
  demandIds: Id[];
  sourceLineCount: number;
  createdCount: number;
  skippedCount: number;
}

/** `PurchaseDemandAllocateForm` —— `version` 是**采购需求**的版本。 */
export interface DemandAllocate {
  demandId: Id;
  purchaseOrderItemId: Id;
  quantity: string;
  supplierId: Id;
  warehouseId: Id;
  version: number;
}

// ------------------------------------------------------------------
// 采购单
// ------------------------------------------------------------------

/**
 * 一条采购行分配（Q13）。
 *
 * 分配身份 = `(purchaseOrderItemId, demandId)`，因此同一个采购行可以承接**多个**需求；
 * 编辑时只改 / 删其中一条不得影响同行其它分配。
 */
export interface Allocation {
  allocationId?: Id;
  demandId?: Id;
  salesOrderId?: Id;
  salesOrderNo?: string;
  salesOrderItemId?: Id;
  skuId?: Id;
  quantity: string;
  /** 需求单位快照（来自销售单位），Q17 要求与 `purchaseUnit` 相等才允许自动分配。 */
  demandUnit?: string;
  /** 需求版本：提交分配时必须带当前值，否则 40972。 */
  demandVersion?: number;
  demandStatus?: string;
}

/** `PurchaseOrderItemVO`。 */
export interface OrderItem {
  id?: Id;
  version?: number;
  skuId?: Id;
  spuCode?: string;
  productName?: string;
  skuCode?: string;
  skuName?: string;
  specValues?: Record<string, unknown> | null;
  /** 采购单位快照（来自 `supplier_sku.purchase_unit`）。 */
  purchaseUnit?: string;
  productType?: string;
  plannedQuantity: string;
  receivedQuantity?: string | null;
  remainingQuantity?: string | null;
  overReceiptQuantity?: string | null;
  purchasePrice: string;
  lineAmount?: string | null;
  sortOrder?: number;
  /** **Q13**：集合，不是单个 `demandId`。 */
  allocations: Allocation[];
}

/** `PurchaseOrderVO`。 */
export interface Order {
  id?: Id;
  orderNo?: string;
  supplierId?: Id;
  supplierCode?: string;
  supplierName?: string;
  purchaserId?: Id | null;
  purchaserName?: string | null;
  warehouseId?: Id;
  warehouseCode?: string;
  warehouseName?: string;
  plannedArrivalDate?: string | null;
  status?: string;
  totalAmount?: string | null;
  /** 收货进度（0–1 的比例，非金额）。 */
  receivedProgress?: string | null;
  remark?: string | null;
  cancelReason?: string | null;
  shortCloseReason?: string | null;
  submittedAt?: string | null;
  cancelledAt?: string | null;
  shortClosedAt?: string | null;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
  items?: OrderItem[];
  allocations?: Allocation[];
  logs?: LogRow[];
}

export interface OrderQuery extends Page {
  orderNo?: string;
  supplierId?: Id;
  purchaserId?: Id;
  warehouseId?: Id;
  status?: string;
  createdFrom?: string;
  createdTo?: string;
}

/** `PurchaseOrderAddForm` / `PurchaseOrderUpdateForm`（后者多 `id` + `version`）。 */
export interface OrderPayload {
  id?: Id;
  version?: number;
  supplierId: Id;
  purchaserId?: Id | null;
  warehouseId: Id;
  plannedArrivalDate?: string | null;
  remark?: string | null;
  items: OrderItemPayload[];
}

export interface OrderItemPayload {
  id?: Id;
  version?: number;
  skuId: Id;
  quantity: string;
  price: string;
  allocations: AllocationPayload[];
}

export interface AllocationPayload {
  demandId: Id;
  quantity: string;
  demandVersion: number;
}

export interface OrderVersionPayload {
  id: Id;
  version: number;
}

export interface OrderCancelPayload extends OrderVersionPayload {
  cancelReason: string;
}

export interface OrderShortClosePayload extends OrderVersionPayload {
  shortCloseReason: string;
}

// ------------------------------------------------------------------
// 收货单
// ------------------------------------------------------------------

/** `PurchaseReceiptItemVO`。P24 恒等式：`remaining = planned − cumulative`、`difference = cumulative − planned`。 */
export interface ReceiptItem {
  id: Id;
  version: number;
  purchaseOrderItemId?: Id;
  skuId?: Id;
  skuCode?: string;
  skuName?: string;
  specValues?: Record<string, unknown> | null;
  purchaseUnit?: string;
  productType?: string;
  plannedQuantity?: string | null;
  receivedQuantity?: string | null;
  cumulativeReceivedQuantity?: string | null;
  remainingQuantity?: string | null;
  overReceiptQuantity?: string | null;
  receiptDifference?: string | null;
  actualWeight?: string | null;
  weightUnit?: string | null;
  weighingSource?: string | null;
  correctionReason?: string | null;
}

/** `PurchaseReceiptVO`。 */
export interface Receipt {
  id?: Id;
  receiptNo?: string;
  purchaseOrderId?: Id;
  purchaseOrderNo?: string;
  supplierId?: Id;
  supplierName?: string;
  warehouseId?: Id;
  warehouseName?: string;
  status?: string;
  receiptMode?: string;
  putawayStatus?: string;
  putawayAt?: string | null;
  putawayBy?: string | null;
  receivedAt?: string | null;
  confirmedAt?: string | null;
  operator?: string | null;
  remark?: string | null;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
  items?: ReceiptItem[];
}

export interface ReceiptQuery extends Page {
  receiptNo?: string;
  purchaseOrderId?: Id;
  supplierId?: Id;
  warehouseId?: Id;
  status?: string;
  receiptMode?: string;
  putawayStatus?: string;
  receivedFrom?: string;
  receivedTo?: string;
}

export interface ReceiptCreatePayload {
  purchaseOrderId: Id;
  /** 入库方式（HD-B1-02）：DIRECT | WAREHOUSE_CONFIRM，必填无默认。 */
  receiptMode: string;
  remark?: string | null;
}

/** `PurchaseReceiptPutawayForm`：仓库确认入库。 */
export interface ReceiptPutawayPayload {
  id: Id;
  version: number;
}

export interface ReceiptUpdatePayload {
  id: Id;
  version: number;
  remark?: string | null;
}

/** `PurchaseReceiptDeleteForm` 只有 `id`（删除本身幂等，不需要版本）。 */
export interface ReceiptDeletePayload {
  id: Id;
}

/** `PurchaseReceiptBatchDeleteForm.receipts` 的元素（`id` + `version` 都必填）。 */
export interface ReceiptVersionPayload {
  id: Id;
  version: number;
}

/**
 * 确认收货的一条明细。
 *
 * 非标品必须给 `actualWeight` + `weightSource='MANUAL'`（`effectiveQuantity` 取实重）；
 * `receivedQuantity` 是**声明数量**，与实重独立。两者都是 4 位定点字符串。
 */
export interface ReceiptConfirmItemPayload {
  receiptItemId: Id;
  version: number;
  receivedQuantity: string;
  actualWeight?: string | null;
  weightSource?: string | null;
  correctionReason?: string | null;
}

export interface ReceiptConfirmPayload {
  id: Id;
  version: number;
  items: ReceiptConfirmItemPayload[];
}

// ------------------------------------------------------------------
// 操作日志 / 仓库
// ------------------------------------------------------------------

/** `PurchaseOperationLogVO`。日志按 `created_at DESC, id DESC` 返回（**最新在前**）。 */
export interface LogRow {
  id: Id;
  purchaseOrderId?: Id | null;
  purchaseReceiptId?: Id | null;
  operationType: string;
  operator?: string;
  reason?: string | null;
  beforeData?: unknown;
  afterData?: unknown;
  createdAt?: string;
}

export interface LogQuery extends Page {
  purchaseOrderId?: Id;
  operationType?: string;
}

/** `WarehouseVO`。 */
export interface Warehouse {
  id: Id;
  warehouseCode?: string;
  name?: string;
  status?: string;
  address?: string | null;
  remark?: string | null;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface WarehouseQuery extends Page {
  warehouseCode?: string;
  name?: string;
  status?: string;
}

export interface WarehousePayload {
  id?: Id;
  version?: number;
  warehouseCode: string;
  name: string;
  address?: string | null;
  remark?: string | null;
}

/** `WarehouseStatusForm`：启用 / 停用（乐观锁 version）。 */
export interface WarehouseStatusPayload {
  id: Id;
  version: number;
}
