/**
 * W6 库存域前端常量（新增文件）。
 *
 * **为什么单独建文件而不塞进 `purchase-const.ts`**：库存域是**与来源无关**的领域原语
 * （未来销售出库 / 调拨 / 盘点都会写同一张流水表），把它的枚举挂到采购常量里，
 * 会让后续波次在「库存常量到底在哪个文件」上反复犯错。W5 的 `purchase-const.ts`
 * 里已经有 `SCM_WAREHOUSE_STATUS_ENUM`（仓库是采购的前置主数据），W6 的库存枚举不重复这一取向。
 *
 * 枚举值与后端**逐字对应**，且与 DB 的 CHECK 白名单同源：
 * - `ScmInventoryMovementTypeEnum` ↔ `ck_inventory_movement_type`
 * - `ScmInventorySourceDocumentTypeEnum` ↔ `uk_inventory_movement_source_active` 的列值
 *
 * 验收：W6 单测、TS 棘轮与 Playwright。
 */
import type { SmartEnum } from '/@/types/smart-enum';

/**
 * 库存流水类型（W6-1 只有 1 个值）。
 *
 * **方向编码在类型里**：`PURCHASE_IN` 即「入」，因此流水没有独立的 `direction` 列，
 * `quantity` 恒为正。新增类型（销售出库 / 调拨 / 盘点 / 报损报溢）必须先扩 DB CHECK 白名单。
 */
export const SCM_INVENTORY_MOVEMENT_TYPE_ENUM: SmartEnum<string> = {
  PURCHASE_IN: { value: 'PURCHASE_IN', desc: '采购入库' },
};

/**
 * 流水来源单据类型（W6-1 只有 1 个值）。
 *
 * 与 `sourceDocumentItemId` 一起构成**稳定唯一源键**（防重锚点）；
 * 人类可读的来源单号用 `receiptNo`（收货单号），W6-1 不设 `movement_no`。
 */
export const SCM_INVENTORY_SOURCE_TYPE_ENUM: SmartEnum<string> = {
  PURCHASE_RECEIPT_ITEM: { value: 'PURCHASE_RECEIPT_ITEM', desc: '采购收货行' },
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
} as const;

export default {
  // 只导出**枚举**：`SCM_INVENTORY_TABLE_ID` 不是枚举，混进 `constantsInfo` 会让
  // `$smartEnumPlugin.getValueDescList` 拿到一个非枚举对象（W5 的 `purchase-const` 同样只导出枚举）。
  SCM_INVENTORY_MOVEMENT_TYPE_ENUM,
  SCM_INVENTORY_SOURCE_TYPE_ENUM,
};
