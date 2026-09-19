/**
 * W6 库存域前端常量（新增文件）。
 *
 * **为什么单独建文件而不塞进 `purchase-const.ts`**：库存域是**与来源无关**的领域原语
 * （出库 / 盘点 / 报损报溢 / 调拨 / 规格转换都会写同一张流水表），把它的枚举挂到采购常量里，
 * 会让后续波次在「库存常量到底在哪个文件」上反复犯错。
 *
 * 枚举值与后端**逐字对应**，且与 DB 的 CHECK 白名单同源：
 * - `ScmInventoryMovementTypeEnum` ↔ `ck_inventory_movement_type`
 * - `ScmInventorySourceDocumentTypeEnum` ↔ `uk_inventory_movement_source_active` 的列值
 * - `ScmInventoryLossGainTypeEnum` / `ScmInventoryLossGainStatusEnum` ↔ 报损报溢单两张 CHECK
 * - `ScmInventoryTransferStatusEnum` ↔ `ck_inventory_transfer_status`
 * - `ScmInventoryWarningStatusEnum` ↔ 读时计算（无 CHECK，但必须与后端枚举一致）
 * - `ScmInventoryConversionTypeEnum` / `ScmInventoryConversionStatusEnum` ↔ 转换单两张 CHECK
 *
 * **新增流水类型时必须同时改四处**：本文件的枚举、后端枚举类、DB CHECK（新迁移），
 * 以及后端 `InventoryMovementQueryForm` 的 `@Pattern` 白名单 ——
 * 漏掉最后一项的表现是「流水写进去了，页面按新类型筛选却报 30001」。
 */
import type { SmartEnum } from '/@/types/smart-enum';

/**
 * 库存流水类型。
 *
 * **方向编码在类型里**：`PURCHASE_IN` / `STOCKTAKE_GAIN` / `GAIN_REPORT` / `TRANSFER_IN` /
 * `CONVERT_IN` 即「入」，其余即「出」，因此流水没有独立的 `direction` 列，
 * `quantity` 恒为正。新增类型必须先扩 DB CHECK 白名单。
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
  CONVERT_OUT: { value: 'CONVERT_OUT', desc: '规格转换出' },
  CONVERT_IN: { value: 'CONVERT_IN', desc: '规格转换入' },
};

/**
 * 流水来源单据类型。
 *
 * 与 `sourceDocumentItemId` 一起构成**稳定唯一源键**（防重锚点）；
 * 人类可读的来源单号用 `receiptNo`（收货单）或 `sourceDocumentNo`。
 *
 * **调拨与规格转换各占两个来源类型**：它们的同一条明细行会产生两条流水（出、入），
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
  CONVERT_OUT_ITEM: { value: 'CONVERT_OUT_ITEM', desc: '转换单行（转出）' },
  CONVERT_IN_ITEM: { value: 'CONVERT_IN_ITEM', desc: '转换单行（转入）' },
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
 * 状态机与出库单**刻意同构**。没有独立的「盘点中」状态：
 * 录入实盘数就是草稿态的编辑动作，`DRAFT` 即「盘点进行中」。
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
 * 在途期间这批货不在任何余额行里，需用「在途库存」报表查看（不进余额表）。
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
 * 判定基准是**可用量**（现有量 − 预留量）而不是现有量。
 * **取等号算正常**：`可用量 == 下限` 是「不低于下限」，属 `NORMAL`。
 */
export const SCM_INVENTORY_WARNING_STATUS_ENUM: SmartEnum<string> = {
  NORMAL: { value: 'NORMAL', desc: '正常' },
  LOW: { value: 'LOW', desc: '低于下限' },
  HIGH: { value: 'HIGH', desc: '高于上限' },
};

/**
 * 规格转换类型（与后端 `ScmInventoryConversionTypeEnum` 逐字对应）。
 *
 * **跨 SKU、同仓库**：源规格 → 目标规格（如整件 → 散装）。跨仓搬运是**调拨**，不是转换。
 * 折算关系（源数量 : 目标数量）由单据显式声明，系统不推断 ——
 * 一箱到底是 9.5 kg 还是 10 kg 取决于供应商与批次，猜错会直接污染两边余额。
 */
export const SCM_INVENTORY_CONVERSION_TYPE_ENUM: SmartEnum<string> = {
  SPLIT: { value: 'SPLIT', desc: '整件拆零' },
  COMBINE: { value: 'COMBINE', desc: '组合拆分' },
};

/**
 * 规格转换单状态（与后端 `ScmInventoryConversionStatusEnum` 逐字对应）。
 *
 * 与报损报溢**刻意同构**：转换会把**两个 SKU** 的余额同时改掉，且折算率是人工声明的，
 * 没有审批等于录单人可以单方面决定「一箱等于多少 kg」。
 */
export const SCM_INVENTORY_CONVERSION_STATUS_ENUM: SmartEnum<string> = {
  PENDING: { value: 'PENDING', desc: '待审核' },
  COMPLETED: { value: 'COMPLETED', desc: '已完成' },
  REJECTED: { value: 'REJECTED', desc: '已驳回' },
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
  CONVERSION: 'scm-inventory-conversion-table',
} as const;

export default {
  // 只导出**枚举**：`SCM_INVENTORY_TABLE_ID` 不是枚举，混进 `constantsInfo` 会让
  // `$smartEnumPlugin.getValueDescList` 拿到一个非枚举对象。
  SCM_INVENTORY_MOVEMENT_TYPE_ENUM,
  SCM_INVENTORY_SOURCE_TYPE_ENUM,
  SCM_INVENTORY_OUTBOUND_STATUS_ENUM,
  SCM_INVENTORY_RESERVATION_STATUS_ENUM,
  SCM_INVENTORY_STOCKTAKE_STATUS_ENUM,
  SCM_INVENTORY_LOSS_GAIN_TYPE_ENUM,
  SCM_INVENTORY_LOSS_GAIN_STATUS_ENUM,
  SCM_INVENTORY_TRANSFER_STATUS_ENUM,
  SCM_INVENTORY_WARNING_STATUS_ENUM,
  SCM_INVENTORY_CONVERSION_TYPE_ENUM,
  SCM_INVENTORY_CONVERSION_STATUS_ENUM,
};
