import Decimal from 'decimal.js';
import type {PriceForm, BatchRow} from '../../../../types/business/scm/pricing';

/** 销售单价/加价金额：两位新录入值或历史四位值，三位和五位以上拒绝。 */
function isSalesMoney(value: string | number | null | undefined): boolean {
  if (value === null || value === undefined || value === '') return false;
  try {
    const parsed = new Decimal(value);
    const places = parsed.decimalPlaces();
    return parsed.isFinite() && !parsed.isNegative() && (places <= 2 || places === 4);
  } catch {
    return false;
  }
}

/** 加价率：最多四位非负小数。 */
function isRate(value: string | number | null | undefined): boolean {
  if (value === null || value === undefined || value === '') return false;
  try {
    const parsed = new Decimal(value);
    return parsed.isFinite() && !parsed.isNegative() && parsed.decimalPlaces() <= 4;
  } catch {
    return false;
  }
}

export function emptyPrice(): PriceForm {
  return {unitPrice: '', effectiveFrom: '', effectiveTo: null};
}

export function validatePrice(f: PriceForm, dimension: 'customerId' | 'customerTypeId'): string[] {
  const errors: string[] = [];
  if (f[dimension] == null) errors.push(dimension === 'customerId' ? '请选择客户' : '请选择客户类型');
  if (f.skuId == null) errors.push('请选择商品规格');
  if ((f.formulaType == null || f.formulaType === 'FIXED') && !isSalesMoney(f.unitPrice)) errors.push('单价须为非负数，最多两位小数');
  if (f.formulaType && f.formulaType !== 'FIXED') {
    const valid = f.formulaType === 'MARKET_PLUS_AMOUNT' ? isSalesMoney(f.formulaParameter) : isRate(f.formulaParameter);
    if (!valid) errors.push(f.formulaType === 'MARKET_PLUS_AMOUNT' ? '加价金额须为非负数，最多两位小数' : '加价率须为非负数，最多四位小数');
  }
  if (!f.effectiveFrom || !Number.isFinite(Date.parse(f.effectiveFrom))) errors.push('请选择生效时间');
  if (f.effectiveTo && (!Number.isFinite(Date.parse(f.effectiveTo)) || Date.parse(f.effectiveTo) <= Date.parse(f.effectiveFrom)))
    errors.push('结束时间必须晚于开始时间');
  return errors;
}

export function validateBatch(rows: BatchRow[]): {rowNumber: number; message: string}[] {
  return rows.flatMap((r) => validatePrice(r, 'customerTypeId').map((message) => ({rowNumber: r.rowNumber, message})));
}
