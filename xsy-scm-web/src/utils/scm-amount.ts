import Decimal from 'decimal.js';

export type DecimalValue = string | number | null | undefined;
export type PriceKind = 'sales' | 'purchase' | 'cost';

const PRICE_SCALE: Record<PriceKind, number> = {sales: 2, purchase: 4, cost: 4};

function decimalOf(value: DecimalValue): Decimal | null {
  if (value === null || value === undefined) return null;
  const text = String(value).trim();
  if (text === '') return null;
  try {
    const result = new Decimal(text);
    return result.isFinite() ? result : null;
  } catch {
    return null;
  }
}

/** 归一为指定小数位；空值或非法值返回空串。 */
export function toScale(value: DecimalValue, scale: number): string {
  const parsed = decimalOf(value);
  return parsed ? parsed.toDecimalPlaces(scale, Decimal.ROUND_HALF_UP).toFixed(scale) : '';
}

/** 销售金额/售价录入统一收敛到两位。 */
export function normalizeMoneyInput(value: DecimalValue): string {
  return toScale(value, 2);
}

/** 提交给后端前先按两位收敛，再转四位定点字符串。 */
export function moneyPayload(value: DecimalValue): string {
  const normalized = normalizeMoneyInput(value);
  return normalized === '' ? '' : toScale(normalized, 4);
}

/** 原始输入是否符合指定位数；历史四位值可先 normalizeMoneyInput 再判断。 */
export function isDecimalScale(value: DecimalValue, scale: number, options: {positive?: boolean; allowZero?: boolean} = {}): boolean {
  const parsed = decimalOf(value);
  if (!parsed || parsed.decimalPlaces() > scale) return false;
  if (options.positive) return parsed.gt(0);
  if (options.allowZero === false) return parsed.gt(0);
  return parsed.gte(0);
}

function formatScaled(value: DecimalValue, scale: number): string {
  const parsed = decimalOf(value);
  if (!parsed) return '';
  const fixed = parsed.toDecimalPlaces(scale, Decimal.ROUND_HALF_UP).toFixed(scale);
  const negative = fixed.startsWith('-');
  const unsigned = negative ? fixed.slice(1) : fixed;
  const [whole, fraction] = unsigned.split('.');
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `${negative ? '-' : ''}${grouped}${fraction ? `.${fraction}` : ''}`;
}

function money(value: DecimalValue, scale: number, empty: string): string {
  if (value === null || value === undefined || value === '') return empty;
  const formatted = formatScaled(value, scale);
  return formatted === '' ? empty : `¥ ${formatted}`;
}

/** 金额：两位小额、千分位；无值显示「未定价」。 */
export function formatAmount(value: DecimalValue): string {
  return money(value, 2, '未定价');
}

/** 金额：无值显示「—」。 */
export function formatAmountOrDash(value: DecimalValue): string {
  return money(value, 2, '—');
}

/** 销售价两位，采购价和成本四位。 */
export function formatPrice(value: DecimalValue, kind: PriceKind = 'sales'): string {
  return money(value, PRICE_SCALE[kind], '—');
}

/** 数量统一四位定点展示。 */
export function formatQuantity(value: DecimalValue): string {
  if (value === null || value === undefined || value === '') return '—';
  return formatScaled(value, 4) || '—';
}
