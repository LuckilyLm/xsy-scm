import type {ScmLocation} from '/@/components/business/scm/map/types';
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

import type {AreaColumns} from '/@/types/business/scm/area';

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

/**
 * `PurchaseDemandSummaryPreviewForm` —— 订单汇总 / 库存缺口预览（Wave 2A §6A，只读）。
 *
 * 半开区间 `[startAt, endAt)` 与 `generate` 同口径；`warehouseId` 必填（销售订单不携带仓库，
 * 缺口只能针对一个仓库算）。可选 `categoryId` / `keyword` 只收窄聚合范围。
 */
export interface DemandSummaryPreviewQuery extends Page {
    startAt: string;
    endAt: string;
    warehouseId: Id;
    categoryId?: Id | null;
    keyword?: string | null;
}

/**
 * `PurchaseDemandSummaryVO` —— 预览聚合行。
 *
 * 数量全部是后端 SQL 内用 `BigDecimal` 算好的四位定点字符串（`null` 与 `"0.0000"` 语义不同），
 * 前端**不得**重算 `availableQuantity` / `stockComparisonGap`（§6A.4）。
 * `UNIT_MISMATCH` 时 `stockComparisonGap` 为 `null`（Q13 单位门禁，不猜折算率）。
 *
 * 预留分三段：`reservedQuantity` 是全仓该 SKU 的总预留，其中 `selectedOrderReservedQuantity`
 * 属于本批预览订单自身，`otherReservedQuantity` 才是其他业务的占用。判断本批是否缺料要看
 * `stockAvailableForSelectedOrders`（= 现有量 − 其他业务预留），不是 `availableQuantity`。
 */
export interface DemandSummaryRow {
    skuId: Id;
    skuCode?: string;
    productName?: string;
    skuName?: string;
    categoryName?: string;
    /** 需求单位（订单销售单位快照）。 */
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
    /** 已确认订单与当前库存/预留的对比差额，**不是**最终净采购建议（§6A.6 未裁决）。 */
    stockComparisonGap?: string | null;
    /** STOCK_ENOUGH / SHORTAGE / ZERO_STOCK / UNIT_MISMATCH / NO_BALANCE。 */
    calculationStatus?: string;
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

/**
 * 批量少收关单（Wave 2B §6.3）：整批共享一个关单原因，`orders` 每行携带各自 `version`。
 *
 * 服务侧在同一事务内按 id 升序逐单套用与单单完全相同的状态机 / 版本校验，任一单非法即整批回滚。
 */
export interface OrderBatchShortClosePayload {
    orders: OrderVersionPayload[];
    shortCloseReason: string;
}

/**
 * 采购单列表导出入参（Wave 2B §6.4，只读）：筛选复用 {@link OrderQuery}。
 *
 * `exportColumns` 只是勾选列的 key 列表，落哪几列、以何顺序由后端
 * `PurchaseOrderExportSupport` 目录裁决；为空或全部未知即导出整目录。分页由服务端强制改为「第 1 页 + 上限行」。
 */
export interface OrderExportPayload extends OrderQuery {
    exportColumns?: string[];
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

/**
 * 按商品收货工作台查询（Wave 2B §6.3，只读）。
 *
 * 范围由后端固定为「可收货」采购单（`SUBMITTED` / `PARTIALLY_RECEIVED`），**不开放状态入参**；
 * 其余筛选仅缩小视图范围，不改变任何聚合口径。
 */
export interface ReceiptItemWorkbenchQuery extends Page {
    supplierId?: Id;
    warehouseId?: Id;
    orderNo?: string;
    keyword?: string;
}

/**
 * 按商品收货工作台行（跨待收采购单按 `skuId + 采购单位` 归并的只读聚合）。
 *
 * 数量全部是后端逐行裁剪后求和的四位定点字符串，前端**绝不重算**；`pendingQuantity` 与
 * `overReceiptQuantity` 分别来自 `SUM(max(计划-已收,0))` / `SUM(max(已收-计划,0))`，二者相加不等于计划或已收。
 */
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

/** `WarehouseVO`。省 / 市 / 区编码与名称快照见 `AreaColumns`（地图 M0 / V40）。 */
export interface Warehouse extends Partial<AreaColumns>, ScmLocation {
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

export interface WarehousePayload extends Partial<AreaColumns>, ScmLocation {
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

/**
 * `WarehouseScopeUpdateForm`：设置某员工可访问的仓库集合。
 *
 * 语义是**整体替换**（不是增量追加）——增量没有任何办法回收一次错误授权，
 * 而回收（失败关闭）恰是这套机制存在的意义。空清单即回收该员工的全部仓库授权。
 */
export interface WarehouseScopeUpdatePayload {
    employeeId: Id;
    warehouseIds: Id[];
}

/**
 * `PurchaseOrderReassignForm`：改派采购归属（{@code scm:purchase:assign}）。
 *
 * `purchaserId` 为 `null` 即「收回归属、留作未分配」（未分配单据只有全量采购范围者可见）；
 * `version` 是乐观锁，编辑接口 `/update` 永不改归属，改派只有 `/reassign` 一条路。
 */
export interface OrderReassignPayload {
    id: Id;
    version: number;
    purchaserId: Id | null;
    reason?: string | null;
}
