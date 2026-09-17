/* 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/constants/business/purchase/purchase-const.ts
复制日期：2026-09-16。Copy First + Adapt。
剪枝：RECEIVE_FLAG_ENUM（A4）、SUPPLIER_STATUS_ENUM / INQUIRY_STATUS_ENUM（A5）、
      PURCHASE_ITEM_STATUS_ENUM（W5 明细状态由对账量派生，无独立列）。
适配：PURCHASE_STATUS_ENUM 5 值数字 → 6 值字符串（A2）；RECEIVE_STATUS_ENUM 3 值 → 2 值（A3）；
      新增 DEMAND_STATUS_ENUM / WAREHOUSE_STATUS_ENUM / SCM_PURCHASE_OPERATION_ENUM；
      新增 Playwright 定位用的表格 DOM id（§9.4）。
验收：W5 单测、TS 棘轮与 Playwright。 */
import type { SmartEnum } from '/@/types/smart-enum';

/**
 * 采购单状态（6 值字符串，与 `PurchaseOrderStateMachine` / V15 的
 * `ck_purchase_order_status` 白名单逐字对应）。
 *
 * <pre>
 * DRAFT ──submit──→ SUBMITTED ──收货──→ PARTIALLY_RECEIVED ──收货──→ RECEIVED
 *   │                   │                        │
 *   │cancel             │cancel                  │short-close
 *   ▼                   ▼                        ▼
 * CANCELLED ←───────────┘                  SHORT_CLOSED
 * </pre>
 *
 * `RECEIVED` / `SHORT_CLOSED` / `CANCELLED` 是终态；`PARTIALLY_RECEIVED` **不允许取消**（P14）。
 */
export const SCM_PURCHASE_STATUS_ENUM: SmartEnum<string> = {
  DRAFT: { value: 'DRAFT', desc: '草稿' },
  SUBMITTED: { value: 'SUBMITTED', desc: '已提交' },
  PARTIALLY_RECEIVED: { value: 'PARTIALLY_RECEIVED', desc: '部分收货' },
  RECEIVED: { value: 'RECEIVED', desc: '已收货' },
  SHORT_CLOSED: { value: 'SHORT_CLOSED', desc: '少收关单' },
  CANCELLED: { value: 'CANCELLED', desc: '已取消' },
};

/** 收货单状态（2 值：草稿 → 已确认，确认后不可改不可删）。 */
export const SCM_RECEIPT_STATUS_ENUM: SmartEnum<string> = {
  DRAFT: { value: 'DRAFT', desc: '草稿' },
  CONFIRMED: { value: 'CONFIRMED', desc: '已确认' },
};

/** 采购需求状态（由 `allocated_quantity` 与 `required_quantity` 派生，必须能回落）。 */
export const SCM_DEMAND_STATUS_ENUM: SmartEnum<string> = {
  PENDING: { value: 'PENDING', desc: '待分配' },
  PARTIALLY_ALLOCATED: { value: 'PARTIALLY_ALLOCATED', desc: '部分分配' },
  ALLOCATED: { value: 'ALLOCATED', desc: '已分配' },
};

/** 仓库状态（W5 只读展示：`WarehouseAddForm` / `WarehouseUpdateForm` 不含 `status`）。 */
export const SCM_WAREHOUSE_STATUS_ENUM: SmartEnum<string> = {
  ENABLED: { value: 'ENABLED', desc: '启用' },
  DISABLED: { value: 'DISABLED', desc: '停用' },
};

/**
 * 采购操作日志类型（11 值，与 `ScmPurchaseOperationTypeEnum` 对应）。
 *
 * 归属由操作类型决定（Q14 四分支）：需求类日志没有单据、分配类靠反查、
 * 收货类同时挂采购单与收货单。
 */
export const SCM_PURCHASE_OPERATION_ENUM: SmartEnum<string> = {
  DEMAND_GENERATE: { value: 'DEMAND_GENERATE', desc: '生成需求' },
  DEMAND_ALLOCATE: { value: 'DEMAND_ALLOCATE', desc: '分配需求' },
  CREATE: { value: 'CREATE', desc: '创建采购单' },
  UPDATE: { value: 'UPDATE', desc: '修改采购单' },
  SUBMIT: { value: 'SUBMIT', desc: '提交采购单' },
  CANCEL: { value: 'CANCEL', desc: '取消采购单' },
  SHORT_CLOSE: { value: 'SHORT_CLOSE', desc: '少收关单' },
  DELETE: { value: 'DELETE', desc: '删除采购单' },
  RECEIPT_CREATE: { value: 'RECEIPT_CREATE', desc: '创建收货单' },
  RECEIPT_UPDATE: { value: 'RECEIPT_UPDATE', desc: '修改收货单' },
  RECEIPT_CONFIRM: { value: 'RECEIPT_CONFIRM', desc: '确认收货' },
};

/**
 * 表格 DOM id（W5 Target Design §9.4）——**给 Playwright 定位用**，不是 `TableOperator` 的 `tableId`。
 *
 * `TableOperator` 的 `tableId` prop 是 `Number`（列配置持久化用），因此另在
 * `TABLE_ID_CONST.BUSINESS` 里以既有的扁平 `SCM_*` 命名注册数字 id。
 */
export const SCM_PURCHASE_TABLE_ID = {
  ORDER: 'scm-purchase-order-table',
  RECEIPT: 'scm-purchase-receipt-table',
  DEMAND: 'scm-purchase-demand-table',
  LOG: 'scm-purchase-log-table',
  WAREHOUSE: 'scm-warehouse-table',
} as const;

export default {
  SCM_PURCHASE_STATUS_ENUM,
  SCM_RECEIPT_STATUS_ENUM,
  SCM_DEMAND_STATUS_ENUM,
  SCM_WAREHOUSE_STATUS_ENUM,
  SCM_PURCHASE_OPERATION_ENUM,
};
