import Decimal from 'decimal.js';
import type {ProductForm, ProductRow, ProductSku} from '/@/types/business/scm/product';

/**
 * 数量录入展示：只去掉补足的尾随零，不改变数值（1.0000 → 1，1.5000 → 1.5）。
 *
 * 本模块会被 node 直接加载（`npm test`），不能用 `/@/` 与相对路径的值导入，
 * 与 `order-form-model` 一样就地实现精度工具。存储、提交仍是四位定点。
 */
export function trimQuantity(value: string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '';
  try {
    return new Decimal(value)
      .toDecimalPlaces(4, Decimal.ROUND_HALF_UP)
      .toFixed(4)
      .replace(/\.?0+$/, '');
  } catch {
    return String(value);
  }
}

/** 数量必须大于 0，且不超过四位小数。 */
function isQuantity(value: string | null | undefined): boolean {
  if (value === null || value === undefined || value === '') return false;
  try {
    const parsed = new Decimal(value);
    return parsed.isFinite() && parsed.gt(0) && parsed.decimalPlaces() <= 4;
  } catch {
    return false;
  }
}

/** 市场价允许两位新录入值，也放行历史四位值；三位和五位以上都拒绝。 */
function isMarketPrice(value: string | null | undefined): boolean {
  if (value === null || value === undefined || value === '') return false;
  try {
    const parsed = new Decimal(value);
    const places = parsed.decimalPlaces();
    return parsed.isFinite() && !parsed.isNegative() && (places <= 2 || places === 4);
  } catch {
    return false;
  }
}

export function emptySku(sortOrder = 0, defaultFlag = false): ProductSku {
  return {
    skuCode: '',
    barcode: '',
    specName: defaultFlag ? '默认商品规格' : '',
    specValues: {},
    saleUnit: '',
    // 商城起订量/递增量是数量，不是金额：整数就写 1，不补四位尾零
    mallMinQuantity: '1',
    mallQuantityStep: '1',
    productType: 'NON_STANDARD',
    marketPrice: '0.0000',
    status: 'ON_SHELF',
    defaultFlag,
    sortOrder,
  };
}

export function emptyProduct(): ProductForm {
  return {
    spuCode: '',
    name: '',
    alias: '',
    categoryId: undefined,
    description: '',
    status: 'ON_SHELF',
    skuList: [emptySku(0, true)],
    images: [],
    mnemonicCode: '',
    brandName: '',
    origin: '',
    shelfLifeDays: null,
    lossRate: null,
    purchaseWarningDays: null,
    invoiceName: '',
    taxCategoryCode: '',
    taxExempt: false,
    taxRate: null,
    masterStatus: 'ENABLED',
    tagIds: [],
  };
}

/** 详情转编辑表单。标签换成 tagId 列表，漏映射会清空商品标签。 */
export function productFormOf(row: ProductRow): ProductForm {
  return {
    spuId: row.spuId,
    version: row.version,
    spuCode: row.spuCode,
    name: row.name,
    alias: row.alias ?? '',
    categoryId: row.categoryId,
    description: row.description ?? '',
    status: row.status,
    // 历史数据是四位定点（1.0000），回填时去掉尾随零，避免编辑框里再出现无意义的四位小数
    skuList: structuredClone(row.skuList).map((sku) => ({
      ...sku,
      mallMinQuantity: trimQuantity(sku.mallMinQuantity),
      mallQuantityStep: trimQuantity(sku.mallQuantityStep),
    })),
    images: structuredClone(row.images ?? []),
    mnemonicCode: row.mnemonicCode ?? '',
    brandName: row.brandName ?? '',
    origin: row.origin ?? '',
    storageMethod: row.storageMethod ?? undefined,
    shelfLifeDays: row.shelfLifeDays ?? null,
    lossRate: row.lossRate ?? null,
    purchaseWarningDays: row.purchaseWarningDays ?? null,
    invoiceName: row.invoiceName ?? '',
    taxCategoryCode: row.taxCategoryCode ?? '',
    taxExempt: row.taxExempt ?? false,
    taxRate: row.taxRate ?? null,
    masterStatus: row.masterStatus,
    tagIds: row.tags.map((tag) => tag.tagId),
  };
}

export function removeSku(rows: ProductSku[], index: number): ProductSku[] {
  if (rows.length < 2) throw new Error('至少保留一个商品规格');
  const removedDefault = rows[index].defaultFlag;
  return rows
    .filter((_, i) => i !== index)
    .map((row, i) => ({
      ...row,
      sortOrder: i,
      defaultFlag: removedDefault ? i === 0 : row.defaultFlag,
    }));
}

export function validateProduct(form: ProductForm): string | undefined {
  // 与数据库约束一致；服务端也会拒绝，这里提前给出提示
  if (form.masterStatus === 'ARCHIVED' && form.status === 'ON_SHELF') return '已归档商品不能处于上架状态';
  if (!form.skuList.length) return '至少保留一个商品规格';
  if (form.skuList.filter((s) => s.defaultFlag).length !== 1) return '请选择且仅选择一个默认商品规格';
  const barcodes = new Set<string>(),
    specs = new Set<string>();
  for (const [index, sku] of form.skuList.entries()) {
    const label = `第 ${index + 1} 个商品规格：`;
    if (!sku.specName.trim() || !sku.saleUnit.trim()) return label + '请填写商品规格名称和单位';
    if (!isMarketPrice(sku.marketPrice)) return label + '市场价须为非负数，最多两位小数';
    // 数量允许按实际需要写小数（斤、kg），但不能为 0，也不能超过后端的四位精度
    if (!isQuantity(sku.mallMinQuantity)) return label + '商城起订量须大于 0，最多四位小数';
    if (!isQuantity(sku.mallQuantityStep)) return label + '商城递增量须大于 0，最多四位小数';
    const barcode = sku.barcode?.trim();
    if (barcode && barcodes.has(barcode)) return label + '条码重复';
    if (barcode) barcodes.add(barcode);
    const entries = Object.entries(sku.specValues);
    if (entries.some(([k, v]) => !k.trim() || !v.trim())) return label + '规格项名称和规格值不能为空';
    const normalized = JSON.stringify(
      entries.map(([k, v]) => [k.trim().toLowerCase(), v.trim().toLowerCase()]).sort(([a], [b]) => a.localeCompare(b))
    );
    if (specs.has(normalized)) return label + '商品规格组合重复';
    specs.add(normalized);
  }
  if (form.images.filter((i) => i.primaryFlag).length > 1) return '最多设置一张主图';
  return undefined;
}
