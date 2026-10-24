import Decimal from 'decimal.js';

/** 把数字控件的值转成四位小数字符串；空值返回 undefined。 */
export function fixed4(value: number | null | undefined): string | undefined {
  if (value === null || value === undefined) return undefined;
  return new Decimal(value).toDecimalPlaces(4, Decimal.ROUND_HALF_UP).toFixed(4);
}
