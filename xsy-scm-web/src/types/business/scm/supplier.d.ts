/**
 * W2 供应商域前端契约（与后端 `module/scm/supplier` 的 Form / VO 一一对应）。
 *
 * 约定（与 `customer.d.ts` 一致）：
 * - 金额字段是**4 位定点字符串**（`"0.0000"`），`null` 表示「未设置」。
 * - 枚举一律用后端字符串码（`ENABLED` / `DISABLED`），不用数字。
 * - `version` 是乐观锁版本；`supplier_sku` 的行级版本也要回传（整表替换时按行比对）。
 */

import type { AreaColumns } from './area';

export type ScmId = string | number;

export type EnableStatus = 'ENABLED' | 'DISABLED';

/** SmartAdmin 统一响应信封（拦截器已解包，此处仅用于类型标注）。 */
export interface ScmResponse<T> {
  code: number;
  ok: boolean;
  msg: string;
  data: T;
}

export interface ScmPage<T> {
  pageNum: number;
  pageSize: number;
  total: number;
  pages: number;
  list: T[];
  emptyFlag: boolean;
}

export interface ScmSortItem {
  column: string;
  isAsc: boolean;
}

// ---------------------------------------------------------------------------
// 供应商
// ---------------------------------------------------------------------------

/** 新建 / 编辑请求体（对应 SupplierAddForm / SupplierUpdateForm，**不含 status**）。 */
export interface SupplierForm extends Partial<AreaColumns> {
  supplierId?: ScmId;
  version?: number;
  supplierCode: string;
  name: string;
  contactName?: string | null;
  contactPhone?: string | null;
  address?: string | null;
  remark?: string | null;
}

/** 列表行（对应 SupplierVO）。 */
export interface SupplierRow extends SupplierForm {
  supplierId: ScmId;
  version: number;
  status: EnableStatus;
  skuCount: number;
  updatedAt: string;
}

/** 详情（对应 SupplierDetailVO）。 */
export interface SupplierDetail extends SupplierRow {
  createdAt?: string;
}

/** 下拉选项（对应 SupplierOptionVO）。 */
export interface SupplierOption {
  supplierId: ScmId;
  supplierCode: string;
  name: string;
}

export interface SupplierQuery {
  pageNum: number;
  pageSize: number;
  searchCount?: boolean;
  keyword?: string;
  status?: EnableStatus;
  sortItemList?: ScmSortItem[];
}

export interface SupplierStatusPayload {
  supplierId: ScmId;
  version: number;
  status: EnableStatus;
}

export interface SupplierDeletePayload {
  supplierId: ScmId;
  version: number;
}

// ---------------------------------------------------------------------------
// 商品-供应商关系（supplier_sku，SKU 级）
// ---------------------------------------------------------------------------

/** 关系行（对应 SupplierSkuVO），含冻结快照列。 */
export interface SupplierSkuRow {
  id: ScmId;
  version: number;
  supplierId: ScmId;
  skuId: ScmId;
  supplierCodeSnapshot: string;
  supplierNameSnapshot: string;
  skuCodeSnapshot: string;
  skuNameSnapshot: string;
  specValuesSnapshot: Record<string, string>;
  purchaseUnit: string;
  referencePrice: string | null;
  /**
   * 采购员（员工）ID。
   *
   * 刻意用 `number | null` 而非 `ScmId`：V2 原生 `employee-select` 的 `value` prop 是
   * `[Number, Array]`，传 `string | number | null` 会触发 TS2322。后端是 Java `Long`，
   * JSON 里本来就是 number。
   */
  purchaserId?: number | null;
  purchaserName?: string | null;
  defaultFlag: boolean;
  status: EnableStatus;
  updatedAt: string;
}

/**
 * 整表替换的单行请求（对应 SupplierSkuItemForm）。
 *
 * - 已存在的行必须带 `id` + `version`（版本不一致 → 40921）；
 * - 新增行不带 `id`；若该 `(supplierId, skuId)` 已存在则按复用处理。
 */
export interface SupplierSkuItem {
  id?: ScmId;
  version?: number;
  skuId: ScmId;
  purchaseUnit: string;
  referencePrice?: string | null;
  /** 采购员（员工）ID；`number | null` 的原因同 `SupplierSkuRow.purchaserId`。 */
  purchaserId?: number | null;
  /** 可多行同时为 true —— 同一供应商允许多条默认来源，前端不得做单选限制。 */
  defaultFlag: boolean;
  status?: EnableStatus;
}

export interface SupplierSkuReplacePayload {
  supplierId: ScmId;
  /** 空数组表示清空该供应商的全部关联，不是「无操作」。 */
  items: SupplierSkuItem[];
}

export interface SupplierSkuQuery {
  pageNum: number;
  pageSize: number;
  searchCount?: boolean;
  supplierId?: ScmId;
  skuId?: ScmId;
  status?: EnableStatus;
  sortItemList?: ScmSortItem[];
}

/** 可下单 SKU 选项（对应 OrderableSkuVO，SPU 与 SKU 必须同时上架）。 */
export interface OrderableSku {
  skuId: ScmId;
  skuCode: string;
  productName: string;
  specValues: Record<string, string>;
}
