import type { ProductForm, ProductSku } from '/@/types/business/scm/product';

export function emptySku(sortOrder = 0, defaultFlag = false): ProductSku {
  return { skuCode: '', barcode: '', specName: defaultFlag ? '默认规格' : '', specValues: {}, saleUnit: '', productType: 'NON_STANDARD', marketPrice: '0.0000', status: 'ON_SHELF', defaultFlag, sortOrder };
}
export function emptyProduct(): ProductForm {
  return { spuCode: '', name: '', alias: '', categoryId: undefined, description: '', status: 'ON_SHELF', skuList: [emptySku(0, true)], images: [] };
}
export function removeSku(rows: ProductSku[], index: number): ProductSku[] {
  if (rows.length < 2) throw new Error('至少保留一个 SKU');
  const removedDefault = rows[index].defaultFlag;
  return rows.filter((_, i) => i !== index).map((row, i) => ({ ...row, sortOrder: i, defaultFlag: removedDefault ? i === 0 : row.defaultFlag }));
}
export function validateProduct(form: ProductForm): string | undefined {
  if (!form.skuList.length) return '至少保留一个 SKU';
  if (form.skuList.filter(s => s.defaultFlag).length !== 1) return '请选择且仅选择一个默认 SKU';
  const codes = new Set<string>(), barcodes = new Set<string>(), specs = new Set<string>();
  for (const [index, sku] of form.skuList.entries()) {
    const label = `第 ${index + 1} 个 SKU：`;
    if (!sku.skuCode.trim() || !sku.specName.trim() || !sku.saleUnit.trim()) return label + '请填写编码、规格名称和单位';
    if (!/^\d+(\.\d{1,4})?$/.test(sku.marketPrice)) return label + '市场价须为非负数，最多四位小数';
    const code = sku.skuCode.trim().toUpperCase();
    if (codes.has(code)) return label + 'SKU 编码重复';
    codes.add(code);
    const barcode = sku.barcode?.trim();
    if (barcode && barcodes.has(barcode)) return label + '条码重复';
    if (barcode) barcodes.add(barcode);
    const entries = Object.entries(sku.specValues);
    if (entries.some(([k, v]) => !k.trim() || !v.trim())) return label + '规格属性名称和值不能为空';
    const normalized = JSON.stringify(entries.map(([k, v]) => [k.trim().toLowerCase(), v.trim().toLowerCase()]).sort(([a], [b]) => a.localeCompare(b)));
    if (specs.has(normalized)) return label + '规格组合重复';
    specs.add(normalized);
  }
  if (form.images.filter(i => i.primaryFlag).length > 1) return '最多设置一张主图';
  return undefined;
}
