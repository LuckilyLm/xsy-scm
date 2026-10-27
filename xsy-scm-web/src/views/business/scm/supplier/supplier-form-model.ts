/** 供应商表单模型：纯函数，可直接单测。新建状态固定启用；关联商品整表替换。 */
import type {EnableStatus, ScmId, SupplierFormModel, SupplierPayload, SupplierSkuItem, SupplierSkuRow} from '/@/types/business/scm/supplier';

/** 非负定点数：最多 14 位整数 + 最多 4 位小数。 */
const DECIMAL = /^\d{1,14}(\.\d{1,4})?$/;

const PHONE = /^(1[3-9]\d{9}|0\d{2,3}-?\d{7,8})$/;

/** 可编辑表格的行：skuId 可空，新建行选完规格前就是空的。 */
export interface SkuDraft {
  id?: ScmId;
  version?: number;
  skuId?: ScmId;
  purchaseUnit: string;
  supplierItemCode?: string | null;
  referencePrice?: string | null;
  /** 采购员 ID：数字或 null。 */
  purchaserId?: number | null;
  /** 允许多行同时为 true。 */
  defaultFlag: boolean;
  status?: EnableStatus;
}

export function emptySupplier(): SupplierFormModel {
  return {
    // 区划和点位字段要显式列全，避免上一条记录的值串进新建供应商。
    longitude: null,
    latitude: null,
    geomCrs: null,
    provinceCode: null,
    provinceName: null,
    cityCode: null,
    cityName: null,
    districtCode: null,
    districtName: null,
    supplierCode: '',
    name: '',
    businessCategoryId: null,
    contactName: '',
    contactPhone: '',
    paymentPeriodDays: 0,
    address: '',
    remark: '',
  };
}

export function emptySkuDraft(): SkuDraft {
  return {purchaseUnit: '', supplierItemCode: null, referencePrice: null, purchaserId: null, defaultFlag: false, status: 'ENABLED'};
}

/** 移除指定行。 */
export function removeSkuDraft(drafts: SkuDraft[], index: number): SkuDraft[] {
  return drafts.filter((_, position) => position !== index);
}

/** 接口行转草稿；保留 id 和 version。 */
export function fromRows(rows: SupplierSkuRow[]): SkuDraft[] {
  return rows.map((row) => ({
    id: row.id,
    version: row.version,
    skuId: row.skuId,
    purchaseUnit: row.purchaseUnit,
    supplierItemCode: row.supplierItemCode ?? null,
    referencePrice: row.referencePrice,
    purchaserId: row.purchaserId ?? null,
    defaultFlag: row.defaultFlag,
    status: row.status,
  }));
}

export function validateSupplier(form: SupplierFormModel): string | undefined {
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
  const supplierCodes = new Set<string>();
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
    const externalCode = draft.supplierItemCode?.trim();
    if (externalCode && externalCode.length > 100) return label + '供应商货号不能超过100个字符';
    if (externalCode && supplierCodes.has(externalCode)) return label + '供应商货号不能重复指向不同商品';
    if (externalCode) supplierCodes.add(externalCode);
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

/** 草稿转替换请求体；不限制默认行数量。 */
export function toReplaceItems(drafts: SkuDraft[]): SupplierSkuItem[] {
  const blankToNull = (value?: string | null): string | null => {
    const text = value == null ? '' : String(value).trim();
    return text === '' ? null : text;
  };
  return drafts.map((draft) => {
    const item: SupplierSkuItem = {
      skuId: draft.skuId as ScmId,
      purchaseUnit: draft.purchaseUnit.trim(),
      supplierItemCode: blankToNull(draft.supplierItemCode),
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

/** 提交前归一化；供应商编码由服务端生成，不提交。 */
export function toSupplierPayload(form: SupplierFormModel): SupplierPayload {
  const blankToNull = (value?: string | null): string | null => {
    const text = value == null ? '' : String(value).trim();
    return text === '' ? null : text;
  };
  const {supplierCode, ...rest} = form;
  return {
    ...rest,
    name: (form.name ?? '').trim(),
    businessCategoryId: form.businessCategoryId ?? null,
    contactName: blankToNull(form.contactName),
    contactPhone: blankToNull(form.contactPhone),
    address: blankToNull(form.address),
    remark: blankToNull(form.remark),
  };
}
