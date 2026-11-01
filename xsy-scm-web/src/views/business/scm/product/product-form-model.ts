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

/**
 * 金额与数量的录入上限。
 *
 * 库里是 NUMERIC(18,4)，这里按常见业务量级卡住多敲的零：上限以内的值原样提交，
 * 超出的给出提示，而不是失焦时静默改成别的数字。
 */
export const MARKET_PRICE_MAX = '99999999.99';
export const MALL_QUANTITY_MAX = '99999999.9999';

function decimalOf(value: string | null | undefined): Decimal | undefined {
  if (value === null || value === undefined || value === '') return undefined;
  try {
    const parsed = new Decimal(value);
    return parsed.isFinite() ? parsed : undefined;
  } catch {
    return undefined;
  }
}

/** 市场价的校验文案，通过返回 undefined；规格行提示和提交前校验共用。 */
export function marketPriceError(value: string | null | undefined): string | undefined {
  const parsed = decimalOf(value);
  if (!parsed) return '市场价须为非负数，最多两位小数';
  // 允许两位的新录入值，也放行历史四位值；三位和五位以上都拒绝
  const places = parsed.decimalPlaces();
  if (parsed.isNegative() || (places > 2 && places !== 4)) return '市场价须为非负数，最多两位小数';
  return parsed.gt(MARKET_PRICE_MAX) ? `市场价不能超过 ${MARKET_PRICE_MAX}` : undefined;
}

/** 商城起订量、递增量的校验文案；字段名由调用方拼在前面。 */
export function mallQuantityError(value: string | null | undefined): string | undefined {
  const parsed = decimalOf(value);
  if (!parsed || !parsed.gt(0) || parsed.decimalPlaces() > 4) return '须大于 0，最多四位小数';
  return parsed.gt(MALL_QUANTITY_MAX) ? `不能超过 ${MALL_QUANTITY_MAX}` : undefined;
}

export function emptySku(sortOrder = 0, defaultFlag = false): ProductSku {
  return {
    skuCode: '',
    barcode: '',
    specName: defaultFlag ? '默认商品规格' : '',
    specValues: {},
    saleUnit: '斤',
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
  if (!form.skuList.length) return '至少保留一个商品规格';
  if (form.skuList.filter((s) => s.defaultFlag).length !== 1) return '请选择且仅选择一个默认商品规格';
  const barcodes = new Set<string>(),
    specs = new Set<string>();
  for (const [index, sku] of form.skuList.entries()) {
    const label = `第 ${index + 1} 个商品规格：`;
    if (!sku.specName.trim() || !sku.saleUnit.trim()) return label + '请填写商品规格名称和单位';
    const priceError = marketPriceError(sku.marketPrice);
    if (priceError) return label + priceError;
    // 数量允许按实际需要写小数（斤、kg），但不能为 0，也不能超过后端的四位精度
    const minQuantityError = mallQuantityError(sku.mallMinQuantity);
    if (minQuantityError) return label + '商城起订量' + minQuantityError;
    const stepError = mallQuantityError(sku.mallQuantityStep);
    if (stepError) return label + '商城递增量' + stepError;
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
