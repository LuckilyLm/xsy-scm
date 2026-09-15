/*
 * 供应商表单模型（纯函数，可被 `node --test` 直接单测）
 *
 * 来源：**新写**。
 * C 没有独立的表单模型（校验 / 默认值内联在 `supplier-list.vue`），按 V2 W1
 * `product-form-model.ts` 的方式抽出。
 *
 * 关键业务规则（与后端一致）：
 * - 供应商新建时状态**强制** `ENABLED`，表单里根本没有状态字段（legacy S7）；
 * - `supplier_sku` 是**整表替换**：已存在的行带 `id` + `version`，新增行不带 `id`；
 *   空数组表示清空全部关联；
 * - **同一供应商允许多条 `defaultFlag = true`**（legacy R12）——校验里绝不加「只允许一条默认」。
 */

import type { EnableStatus, ScmId, SupplierForm, SupplierSkuItem, SupplierSkuRow } from '/@/types/business/scm/supplier';

/** 非负定点数：最多 14 位整数 + 最多 4 位小数。 */
const DECIMAL = /^\d{1,14}(\.\d{1,4})?$/;

const PHONE = /^(1[3-9]\d{9}|0\d{2,3}-?\d{7,8})$/;

/**
 * 可编辑表格的行。
 *
 * 与 `SupplierSkuItem` 的唯一区别是 `skuId` 允许为空 —— 新建行在用户选完商品规格之前就是空的。
 * 提交时由 {@link toReplaceItems} 负责把空行挡掉。
 */
export interface SkuDraft {
  id?: ScmId;
  version?: number;
  skuId?: ScmId;
  purchaseUnit: string;
  referencePrice?: string | null;
  /**
   * 采购员（员工）ID。
   *
   * 用 `number | null` 而非 `ScmId`：V2 原生 `employee-select` 的 `value` prop 是
   * `[Number, Array]`，传 `string | number | null` 会触发 TS2322。后端是 Java `Long`，
   * JSON 里本来就是 number。
   */
  purchaserId?: number | null;
  /** 多行可以同时为 true，这不是缺陷而是 legacy 事实。 */
  defaultFlag: boolean;
  status?: EnableStatus;
}

export function emptySupplier(): SupplierForm {
  return { supplierCode: '', name: '', contactName: '', contactPhone: '', address: '', remark: '' };
}

export function emptySkuDraft(): SkuDraft {
  return { purchaseUnit: '', referencePrice: null, purchaserId: null, defaultFlag: false, status: 'ENABLED' };
}

/** 移除某一行（纯函数，便于 `node --test` 直接单测）。 */
export function removeSkuDraft(drafts: SkuDraft[], index: number): SkuDraft[] {
  return drafts.filter((_, position) => position !== index);
}

/** 服务端返回的行 → 可编辑草稿（保留 id / version，提交时才能走「保留」分支）。 */
export function fromRows(rows: SupplierSkuRow[]): SkuDraft[] {
  return rows.map((row) => ({
    id: row.id,
    version: row.version,
    skuId: row.skuId,
    purchaseUnit: row.purchaseUnit,
    referencePrice: row.referencePrice,
    purchaserId: row.purchaserId ?? null,
    defaultFlag: row.defaultFlag,
    status: row.status,
  }));
}

export function validateSupplier(form: SupplierForm): string | undefined {
  if (!form.supplierCode?.trim()) {
    return '请输入供应商编码';
  }
  if (!form.name?.trim()) {
    return '请输入供应商名称';
  }
  const phone = form.contactPhone?.trim();
  if (phone && !PHONE.test(phone)) {
    return '联系电话格式不正确';
  }
  return undefined;
}

export function validateSkuDrafts(drafts: SkuDraft[]): string | undefined {
  const seen = new Set<string>();
  for (const [index, draft] of drafts.entries()) {
    const label = `第 ${index + 1} 行：`;
    if (draft.skuId == null) {
      return label + '请选择商品规格';
    }
    const key = String(draft.skuId);
    if (seen.has(key)) {
      return label + '该商品规格已存在，不能重复添加';
    }
    seen.add(key);
    if (!draft.purchaseUnit?.trim()) {
      return label + '请填写采购单位';
    }
    const price = draft.referencePrice?.trim();
    if (price && !DECIMAL.test(price)) {
      return label + '参考价须为非负数，最多四位小数';
    }
  }
  return undefined;
}

/** 草稿 → 替换请求体。**不校验** `defaultFlag` 的基数（R12）。 */
export function toReplaceItems(drafts: SkuDraft[]): SupplierSkuItem[] {
  const blankToNull = (value?: string | null): string | null => {
    const text = value == null ? '' : String(value).trim();
    return text === '' ? null : text;
  };
  return drafts.map((draft) => {
    const item: SupplierSkuItem = {
      skuId: draft.skuId as ScmId,
      purchaseUnit: draft.purchaseUnit.trim(),
      referencePrice: blankToNull(draft.referencePrice),
      purchaserId: draft.purchaserId ?? null,
      defaultFlag: draft.defaultFlag,
      status: draft.status ?? 'ENABLED',
    };
    if (draft.id != null) {
      item.id = draft.id;
      item.version = draft.version;
    }
    return item;
  });
}

export function toSupplierPayload(form: SupplierForm): SupplierForm {
  const blankToNull = (value?: string | null): string | null => {
    const text = value == null ? '' : String(value).trim();
    return text === '' ? null : text;
  };
  return {
    ...form,
    supplierCode: (form.supplierCode ?? '').trim().toUpperCase(),
    name: (form.name ?? '').trim(),
    contactName: blankToNull(form.contactName),
    contactPhone: blankToNull(form.contactPhone),
    address: blankToNull(form.address),
    remark: blankToNull(form.remark),
  };
}
