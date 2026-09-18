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
  /** 收货单号（Q9：人类可读来源，W6-1 不设 movement_no）。 */
  receiptNo?: string;
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
  /** W6-1 只有 `PURCHASE_IN`。 */
  movementType?: string;
  /** W6-1 只有 `PURCHASE_RECEIPT_ITEM`。 */
  sourceDocumentType?: string;
  sourceDocumentId?: Id;
  occurredFrom?: string | null;
  occurredTo?: string | null;
}
