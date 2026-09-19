/**
 * W6 库存域前端常量（新增文件）。
 *
 * **为什么单独建文件而不塞进 `purchase-const.ts`**：库存域是**与来源无关**的领域原语
 * （销售出库 / 盘点 / 报损报溢 / 调拨都会写同一张流水表），把它的枚举挂到采购常量里，
 * 会让后续波次在「库存常量到底在哪个文件」上反复犯错。W5 的 `purchase-const.ts`
 * 里已经有 `SCM_WAREHOUSE_STATUS_ENUM`（仓库是采购的前置主数据），W6 的库存枚举不重复这一取向。
 *
 * 枚举值与后端**逐字对应**，且与 DB 的 CHECK 白名单同源：
 * - `ScmInventoryMovementTypeEnum` ↔ `ck_inventory_movement_type`
 * - `ScmInventorySourceDocumentTypeEnum` ↔ `uk_inventory_movement_source_active` 的列值
 * - `ScmInventoryLossGainTypeEnum` ↔ `ck_inventory_loss_gain_type`
 * - `ScmInventoryLossGainStatusEnum` ↔ `ck_inventory_loss_gain_status`
 * - `ScmInventoryTransferStatusEnum` ↔ `ck_inventory_transfer_status`
 *
 * **新增流水类型时必须同时改四处**：本文件的枚举、后端枚举类、DB CHECK（新迁移），
 * 以及后端 `InventoryMovementQueryForm` 的 `@Pattern` 白名单 ——
 * 漏掉最后一项的表现是「流水写进去了，页面按新类型筛选却报 30001」。
 *
 * 验收：W6 单测、TS 棘轮与 Playwright。
 */
import type { SmartEnum } from '/@/types/smart-enum';

/**
 * 库存流水类型。
 *
 * **方向编码在类型里**：`PURCHASE_IN` / `STOCKTAKE_GAIN` / `GAIN_REPORT` / `TRANSFER_IN` 即「入」，
 * `SALES_OUT` / `STOCKTAKE_LOSS` / `LOSS_REPORT` / `TRANSFER_OUT` 即「出」，
 * 因此流水没有独立的 `direction` 列，`quantity` 恒为正。新增类型必须先扩 DB CHECK 白名单。
 */
export const SCM_INVENTORY_MOVEMENT_TYPE_ENUM: SmartEnum<string> = {
  PURCHASE_IN: { value: 'PURCHASE_IN', desc: '采购入库' },
  SALES_OUT: { value: 'SALES_OUT', desc: '销售出库' },
  STOCKTAKE_GAIN: { value: 'STOCKTAKE_GAIN', desc: '盘盈' },
  STOCKTAKE_LOSS: { value: 'STOCKTAKE_LOSS', desc: '盘亏' },
  LOSS_REPORT: { value: 'LOSS_REPORT', desc: '报损' },
  GAIN_REPORT: { value: 'GAIN_REPORT', desc: '报溢' },
  TRANSFER_OUT: { value: 'TRANSFER_OUT', desc: '调拨转出' },
  TRANSFER_IN: { value: 'TRANSFER_IN', desc: '调拨转入' },
};

/**
 * 流水来源单据类型。
 *
 * 与 `sourceDocumentItemId` 一起构成**稳定唯一源键**（防重锚点）；
 * 人类可读的来源单号用 `receiptNo`（收货单）或 `sourceDocumentNo`（出库 / 盘点 / 报损报溢 / 调拨单）。
 *
 * **调拨占两个来源类型**：同一条调拨明细行会产生两条流水（发出、收货），
 * 而唯一索引只认 `(source_document_type, source_document_item_id)` ——
 * 共用一个来源类型会让第二条流水插不进去。因此方向被编进了来源类型。
 */
export const SCM_INVENTORY_SOURCE_TYPE_ENUM: SmartEnum<string> = {
  PURCHASE_RECEIPT_ITEM: { value: 'PURCHASE_RECEIPT_ITEM', desc: '采购收货行' },
  SALES_OUTBOUND_ITEM: { value: 'SALES_OUTBOUND_ITEM', desc: '出库单行' },
  SALES_ORDER_ITEM: { value: 'SALES_ORDER_ITEM', desc: '销售订单行' },
  STOCKTAKE_ITEM: { value: 'STOCKTAKE_ITEM', desc: '盘点单行' },
  LOSS_GAIN_ITEM: { value: 'LOSS_GAIN_ITEM', desc: '报损报溢单行' },
  TRANSFER_OUT_ITEM: { value: 'TRANSFER_OUT_ITEM', desc: '调拨单行（转出）' },
  TRANSFER_IN_ITEM: { value: 'TRANSFER_IN_ITEM', desc: '调拨单行（转入）' },
};

/**
 * 出库单状态（与后端 `ScmInventoryOutboundStatusEnum` 逐字对应）。
 *
 * `DRAFT → CONFIRMED`，草稿可 `CANCELLED`。**已确认不可回退** ——
 * 流水 append-only，冲销必须新增反向流水。
 */
export const SCM_INVENTORY_OUTBOUND_STATUS_ENUM: SmartEnum<string> = {
  DRAFT: { value: 'DRAFT', desc: '草稿' },
  CONFIRMED: { value: 'CONFIRMED', desc: '已确认' },
  CANCELLED: { value: 'CANCELLED', desc: '已取消' },
};

/** 预留状态（与后端 `ScmInventoryReservationStatusEnum` 逐字对应）。 */
export const SCM_INVENTORY_RESERVATION_STATUS_ENUM: SmartEnum<string> = {
  ACTIVE: { value: 'ACTIVE', desc: '生效中' },
  RELEASED: { value: 'RELEASED', desc: '已释放' },
  CONSUMED: { value: 'CONSUMED', desc: '已消耗' },
};

/**
 * 盘点单状态（与后端 `ScmInventoryStocktakeStatusEnum` 逐字对应）。
 *
 * 状态机与出库单**刻意同构**，保持全仓单据状态语义一致。
 * 没有独立的「盘点中」状态：录入实盘数就是草稿态的编辑动作，`DRAFT` 即「盘点进行中」。
 */
export const SCM_INVENTORY_STOCKTAKE_STATUS_ENUM: SmartEnum<string> = {
  DRAFT: { value: 'DRAFT', desc: '草稿' },
  CONFIRMED: { value: 'CONFIRMED', desc: '已确认' },
  CANCELLED: { value: 'CANCELLED', desc: '已取消' },
};

/**
 * 报损报溢单的调整类型（与后端 `ScmInventoryLossGainTypeEnum` 逐字对应）。
 *
 * **方向是单据级属性**：一张单要么全报损、要么全报溢，行上的数量恒为正。
 */
export const SCM_INVENTORY_LOSS_GAIN_TYPE_ENUM: SmartEnum<string> = {
  LOSS: { value: 'LOSS', desc: '报损' },
  OVERFLOW: { value: 'OVERFLOW', desc: '报溢' },
};

/**
 * 报损报溢单状态（与后端 `ScmInventoryLossGainStatusEnum` 逐字对应）。
 *
 * **没有 DRAFT**：报损报溢创建即提交（待审核），因为「把货从账上抹掉」需要制衡。
 */
export const SCM_INVENTORY_LOSS_GAIN_STATUS_ENUM: SmartEnum<string> = {
  PENDING: { value: 'PENDING', desc: '待审核' },
  COMPLETED: { value: 'COMPLETED', desc: '已完成' },
  REJECTED: { value: 'REJECTED', desc: '已驳回' },
};

/**
 * 调拨单状态（与后端 `ScmInventoryTransferStatusEnum` 逐字对应）。
 *
 * **两步式**：`DRAFT → SHIPPED（在途）→ RECEIVED`，草稿可 `CANCELLED`。
 * `SHIPPED` **不可取消** —— 货已经物理离开源仓，账上只能靠一张反向调拨单冲回。
 * 在途期间这批货不在任何余额行里（没有虚拟在途仓），全仓总库存会暂时减少。
 */
export const SCM_INVENTORY_TRANSFER_STATUS_ENUM: SmartEnum<string> = {
  DRAFT: { value: 'DRAFT', desc: '草稿' },
  SHIPPED: { value: 'SHIPPED', desc: '在途' },
  RECEIVED: { value: 'RECEIVED', desc: '已完成' },
  CANCELLED: { value: 'CANCELLED', desc: '已取消' },
};

/**
 * 库存预警状态（与后端 `ScmInventoryWarningStatusEnum` 逐字对应）。
 *
 * **这是派生值，不落库**：完全由 `(阈值, 可用量)` 决定，读时计算。
 * 判定基准是**可用量**（现有量 − 预留量）而不是现有量 —— 下限的业务含义是
 * 「还够不够发货」：20 kg 在库但 18 kg 已预留时可用只有 2 kg，必须触发补货预警。
 *
 * **取等号算正常**：`可用量 == 下限` 是「不低于下限」，属 `NORMAL`。
 */
export const SCM_INVENTORY_WARNING_STATUS_ENUM: SmartEnum<string> = {
  NORMAL: { value: 'NORMAL', desc: '正常' },
  LOW: { value: 'LOW', desc: '低于下限' },
  HIGH: { value: 'HIGH', desc: '高于上限' },
};

/**
 * 表格 DOM id —— **给 Playwright 定位用**，不是 `TableOperator` 的 `tableId`。
 *
 * `TableOperator` 的 `tableId` prop 是 `Number`（列配置持久化用），因此另在
 * `TABLE_ID_CONST.BUSINESS` 里以既有的扁平 `SCM_*` 命名注册数字 id。
 */
export const SCM_INVENTORY_TABLE_ID = {
  BALANCE: 'scm-inventory-balance-table',
  MOVEMENT: 'scm-inventory-movement-table',
  OUTBOUND: 'scm-inventory-outbound-table',
  RESERVATION: 'scm-inventory-reservation-table',
  STOCKTAKE: 'scm-inventory-stocktake-table',
  LOSS_GAIN: 'scm-inventory-loss-gain-table',
  TRANSFER: 'scm-inventory-transfer-table',
  WARNING: 'scm-inventory-warning-table',
  WARNING_THRESHOLD: 'scm-inventory-warning-threshold-table',
} as const;

export default {
  // 只导出**枚举**：`SCM_INVENTORY_TABLE_ID` 不是枚举，混进 `constantsInfo` 会让
  // `$smartEnumPlugin.getValueDescList` 拿到一个非枚举对象（W5 的 `purchase-const` 同样只导出枚举）。
  SCM_INVENTORY_MOVEMENT_TYPE_ENUM,
  SCM_INVENTORY_SOURCE_TYPE_ENUM,
  SCM_INVENTORY_OUTBOUND_STATUS_ENUM,
  SCM_INVENTORY_RESERVATION_STATUS_ENUM,
  SCM_INVENTORY_STOCKTAKE_STATUS_ENUM,
  SCM_INVENTORY_LOSS_GAIN_TYPE_ENUM,
  SCM_INVENTORY_LOSS_GAIN_STATUS_ENUM,
  SCM_INVENTORY_TRANSFER_STATUS_ENUM,
  SCM_INVENTORY_WARNING_STATUS_ENUM,
};
