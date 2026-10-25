import type {ScmLocation} from '/@/components/business/scm/map/types';
/** 采购模块的请求和响应类型，与后端一致。数量和金额用定点字符串，null 与 0 含义不同。 */

import type {AreaColumns} from '/@/types/business/scm/area';

export type Id = string | number;

/** 分页入参。`pageNum` / `pageSize` 必填，否则后端会报空指针。 */
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
  /** 生成这条需求的批次；一次性生成时为空。 */
  calculationBatchId?: Id | null;
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

/** `PurchaseDemandGenerateForm` —— 半开区间 `[startAt, endAt)`。 */
export interface DemandGenerate {
  startAt: string;
  endAt: string;
  warehouseId: Id;
  supplierId?: Id | null;
  purchaserId?: Id | null;
}

export interface DemandBatchCreate extends DemandGenerate {
  categoryId?: Id | null;
  keyword?: string | null;
}

export interface DemandBatchGenerate {
  batchId: Id;
}

export interface DemandCalculationBatch {
  batchId: Id;
  status: 'READY' | 'GENERATED' | string;
  sourceLineCount: number;
  candidateLineCount: number;
  generatedCount: number;
  skippedCount: number;
  summary?: DemandBatchSummaryRow[];
}

/** 采购需求生成批次的历史记录。数值是生成时的结果，不代表当前库存。 */
export interface DemandBatchDetail {
  batchId: Id;
  status: 'READY' | 'GENERATED' | string;
  startAt?: string;
  endAt?: string;
  warehouseId?: Id;
  warehouseName?: string | null;
  supplierId?: Id | null;
  supplierName?: string | null;
  /** 批次归属采购员；回看权限按它判断。 */
  purchaserId?: Id | null;
  purchaserName?: string | null;
  categoryId?: Id | null;
  keyword?: string | null;
  sourceLineCount: number;
  candidateLineCount: number;
  generatedCount: number;
  skippedCount: number;
  createdAt?: string;
  createdBy?: string;
  generatedAt?: string | null;
  summary?: DemandBatchSummaryRow[];
  items?: DemandBatchItem[];
}

/** 批次生成时的 SKU 汇总；null 表示单位不一致，不猜折算率。 */
export interface DemandBatchSummaryRow {
  skuId: Id;
  skuCode?: string;
  productName?: string;
  skuName?: string;
  demandUnit?: string;
  orderDemandQuantity?: string | null;
  onHandQuantity?: string | null;
  reservedQuantity?: string | null;
  selectedOrderReservedQuantity?: string | null;
  otherReservedQuantity?: string | null;
  availableQuantity?: string | null;
  stockAvailableForSelectedOrders?: string | null;
  stockComparisonGap?: string | null;
  inTransitQuantity?: string | null;
  purchaseCoverageQuantity?: string | null;
  netPurchaseGap?: string | null;
  calculationStatus?: string;
}

/** 批次内的逐行建议。`requiredQuantity` 可能小于 `sourceQuantity`；`existingDemandId` 非空表示该行已有需求。 */
export interface DemandBatchItem {
  lineNo: number;
  salesOrderNo?: string;
  sourceConfirmedAt?: string;
  skuId?: Id;
  skuCode?: string;
  productName?: string;
  skuName?: string;
  specValues?: Record<string, unknown>;
  demandUnit?: string;
  productType?: string;
  sourceQuantity?: string | null;
  requiredQuantity?: string | null;
  existingDemandId?: Id | null;
}

/** `PurchaseDemandService.GenerateResult`。 */
export interface GenerateResult {
  demandIds: Id[];
  sourceLineCount: number;
  createdCount: number;
  skippedCount: number;
}

/** `PurchaseDemandAllocateForm` —— `version` 是采购需求的版本。 */
export interface DemandAllocate {
  demandId: Id;
  purchaseOrderItemId: Id;
  quantity: string;
  supplierId: Id;
  warehouseId: Id;
  version: number;
}

/** 订单汇总和库存缺口预览。时间范围左闭右开，`warehouseId` 必填。 */
export interface DemandSummaryPreviewQuery extends Page {
  startAt: string;
  endAt: string;
  warehouseId: Id;
  categoryId?: Id | null;
  keyword?: string | null;
}

/** 采购需求汇总预览，由后端计算。单位不一致时缺口留空。 */
export interface DemandSummaryRow {
  skuId: Id;
  skuCode?: string;
  productName?: string;
  skuName?: string;
  categoryName?: string;
  /** 需求单位（取自订单销售单位）。 */
  demandUnit?: string;
  /** 余额记账单位；`NO_BALANCE` 时为 null。 */
  inventoryUnit?: string | null;
  sourceOrderCount?: number;
  sourceLineCount?: number;
  orderDemandQuantity?: string | null;
  onHandQuantity?: string | null;
  reservedQuantity?: string | null;
  selectedOrderReservedQuantity?: string | null;
  otherReservedQuantity?: string | null;
  availableQuantity?: string | null;
  stockAvailableForSelectedOrders?: string | null;
  /** 已确认订单与当前库存、预留的差额，不是最终净采购建议（计算方式待定）。 */
  stockComparisonGap?: string | null;
  /** 未收采购覆盖：在途未收量扣除已分配需求后的余量。 */
  inTransitQuantity?: string | null;
  /** 已有采购需求分配覆盖量。 */
  purchaseCoverageQuantity?: string | null;
  /** 扣除库存、在途未收和采购覆盖后的净采购缺口。 */
  netPurchaseGap?: string | null;
  /** STOCK_ENOUGH / SHORTAGE / ZERO_STOCK / UNIT_MISMATCH / NO_BALANCE。 */
  calculationStatus?: string;
}

// ------------------------------------------------------------------
// 采购单
// ------------------------------------------------------------------

/** 采购行分配。同一采购行可以分配多个需求，编辑其中一条不影响其他分配。 */
export interface Allocation {
  allocationId?: Id;
  demandId?: Id;
  salesOrderId?: Id;
  salesOrderNo?: string;
  salesOrderItemId?: Id;
  skuId?: Id;
  quantity: string;
  /** 需求单位（来自销售单位），必须与采购单位一致才允许自动分配。 */
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
  /** 采购单位，来自供应商商品设置。 */
  purchaseUnit?: string;
  productType?: string;
  plannedQuantity: string;
  receivedQuantity?: string | null;
  remainingQuantity?: string | null;
  overReceiptQuantity?: string | null;
  purchasePrice: string;
  lineAmount?: string | null;
  sortOrder?: number;
  /** 集合，不是单个 `demandId`。 */
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

/** 批量少收关单：整批共用一个原因，每单带各自 version；任一单失败整批回滚。 */
export interface OrderBatchShortClosePayload {
  orders: OrderVersionPayload[];
  shortCloseReason: string;
}

/** 采购单导出入参。`exportColumns` 为空时导出全部列，分页由后端固定为第一页。 */
export interface OrderExportPayload extends OrderQuery {
  exportColumns?: string[];
}

// ------------------------------------------------------------------
// 收货单
// ------------------------------------------------------------------

/** `PurchaseReceiptItemVO` 的恒等式：`remaining = planned − cumulative`、`difference = cumulative − planned`。 */
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
  /** 已登记的收货批次；未启用批次追踪的仓库为空。 */
  lots?: ReceiptItemLot[];
}

/** 收货行上的一条批次记录。 */
export interface ReceiptItemLot {
  receiptItemId?: Id;
  lotId?: Id;
  lotNo?: string;
  sourceLotNo?: string | null;
  quantity?: string | null;
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
  /** 该仓库是否启用批次追踪：决定确认弹窗是否要求登记批次。 */
  warehouseLotTrackingEnabled?: boolean;
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
  /** 入库方式：DIRECT | WAREHOUSE_CONFIRM，必填无默认。 */
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

/** 删除收货单只需要 id。 */
export interface ReceiptDeletePayload {
  id: Id;
}

/** `PurchaseReceiptBatchDeleteForm.receipts` 的元素（`id` + `version` 都必填）。 */
export interface ReceiptVersionPayload {
  id: Id;
  version: number;
}

/** 确认收货的一条明细。非标品必须提供实重，数量和实重都是 4 位定点字符串。 */
export interface ReceiptConfirmItemPayload {
  receiptItemId: Id;
  version: number;
  receivedQuantity: string;
  actualWeight?: string | null;
  weightSource?: string | null;
  correctionReason?: string | null;
  /** 批次仓必填：数量之和必须等于本次有效收货量；非批次仓必须为空。 */
  lots?: ReceiptConfirmLot[];
}

/** 收货行上的一个批次：来源生产批号可空，数量必填（四位定点字符串）。 */
export interface ReceiptConfirmLot {
  sourceLotNo?: string | null;
  quantity: string;
}

export interface ReceiptConfirmPayload {
  id: Id;
  version: number;
  items: ReceiptConfirmItemPayload[];
}

/** 按商品收货工作台查询；状态固定为可收货的采购单。 */
export interface ReceiptItemWorkbenchQuery extends Page {
  supplierId?: Id;
  warehouseId?: Id;
  orderNo?: string;
  keyword?: string;
}

/** 工作台一行：按商品规格和采购单位合并多张采购单，数量由后端计算。 */
export interface ReceiptItemWorkbenchRow {
  skuId: Id;
  skuCode?: string;
  skuName?: string;
  productName?: string;
  purchaseUnit?: string;
  productType?: string;
  orderCount?: number;
  lineCount?: number;
  plannedQuantity?: string | null;
  receivedQuantity?: string | null;
  pendingQuantity?: string | null;
  overReceiptQuantity?: string | null;
}

// ------------------------------------------------------------------
// 操作日志 / 仓库
// ------------------------------------------------------------------

/** `PurchaseOperationLogVO`。日志按 `created_at DESC, id DESC` 返回（最新在前）。 */
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

/** `WarehouseVO`。省市区编码和名称见 `AreaColumns`。 */
export interface Warehouse extends Partial<AreaColumns>, ScmLocation {
  id: Id;
  warehouseCode?: string;
  name?: string;
  status?: string;
  address?: string | null;
  remark?: string | null;
  /** 是否启用批次追踪；开启后不可关闭。 */
  lotTrackingEnabled?: boolean;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface WarehouseQuery extends Page {
  warehouseCode?: string;
  name?: string;
  status?: string;
}

/** 新建 / 编辑表单模型（UI 侧）：比写请求体多一个只读的编码回显。 */
export interface WarehouseFormModel extends Partial<AreaColumns>, ScmLocation {
  id?: Id;
  version?: number;
  /** 只读回显：编码由服务端生成，不进入写请求（见 `WarehousePayload`）。 */
  warehouseCode?: string;
  name: string;
  address?: string | null;
  remark?: string | null;
  /** 是否启用批次追踪：只在新建时提交，编辑态只读回显。 */
  lotTrackingEnabled?: boolean;
}

/** 写请求体。不含 `warehouseCode`，编码由服务端生成。 */
export type WarehousePayload = Omit<WarehouseFormModel, 'warehouseCode'>;

/** `WarehouseStatusForm`：启用 / 停用（乐观锁 version）。 */
export interface WarehouseStatusPayload {
  id: Id;
  version: number;
}

// ------------------------------------------------------------------
// 仓库授权（employee_warehouse_scope）
// ------------------------------------------------------------------

/** `WarehouseScopeEmployeeVO`：某仓库下被授权的员工行（只读）。 */
export interface WarehouseScopeEmployee {
  employeeId: Id;
  loginName: string;
  actualName: string;
  /** 员工是否已停用：授权行可能早于离职，维护页必须看得出来，否则会出现「仓只有离职人能看」。 */
  disabledFlag: boolean;
}

/** `WarehouseScopeWarehouseVO`：某员工被授权的仓库行（只读）。 */
export interface WarehouseScopeWarehouse {
  warehouseId: Id;
  warehouseCode: string;
  warehouseName: string;
  /** 仓库当前启停状态：授权到已停用仓库不立刻出问题，但维护页应看得见。 */
  warehouseStatus: string;
}

/** 设置员工可访问的仓库，整体替换；空数组表示收回全部授权。 */
export interface WarehouseScopeUpdatePayload {
  employeeId: Id;
  warehouseIds: Id[];
}

/** 改派采购归属。`purchaserId` 为 null 表示收回；`version` 用于乐观锁。 */
export interface OrderReassignPayload {
  id: Id;
  version: number;
  purchaserId: Id | null;
  reason?: string | null;
}
