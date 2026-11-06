import Decimal from 'decimal.js';

/** 大屏各指标统一使用此处的数字与金额格式化，避免同一数值在不同面板显示格式不一致。 */

/** 把后端返回的小数字符串安全转成 number。非法值一律返回 0，不返回 NaN。 */
export function toNumber(value: string | number | null | undefined): number {
  if (value === null || value === undefined || value === '') {
    return 0;
  }
  const num = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(num) ? num : 0;
}

/** 金额：千分位 + 固定两位小数。用于销售额、采购额这类金额。 */
export function formatAmount(value: string | number | null | undefined): string {
  return toNumber(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

/** 数量：千分位 + 最多两位小数，整数不带小数点。 */
export function formatQty(value: string | number | null | undefined): string {
  return toNumber(value).toLocaleString('zh-CN', {maximumFractionDigits: 2});
}

/** 整数计数（订单数、客户数等）。 */
export function formatInt(value: string | number | null | undefined): string {
  return Math.round(toNumber(value)).toLocaleString('zh-CN');
}

/** 紧凑金额：≥1 万显示为「1.2万」，否则按金额格式。用于地图节点这类空间紧张的地方。 */
export function formatCompact(value: string | number | null | undefined): string {
  const num = toNumber(value);
  if (Math.abs(num) >= 10000) {
    return `${(num / 10000).toFixed(1)}万`;
  }
  return formatQty(num);
}

/** 环比：与上一期相比的百分比；基数为 0 时返回 null，由调用方显示「—」。 */
export function formatDelta(current: number, previous: number): number | null {
  if (previous === 0) {
    return null;
  }
  return ((current - previous) / previous) * 100;
}

/** 环比文案：带符号、一位小数；无基数时返回「—」。 */
export function formatDeltaText(delta: number | null): string {
  if (delta === null) {
    return '—';
  }
  const sign = delta > 0 ? '+' : '';
  return `${sign}${delta.toFixed(1)}%`;
}

/** 环比方向：涨、跌、持平、无基数。只表方向，好坏由调用方判断。 */
export type DeltaDirection = 'up' | 'down' | 'flat' | 'unknown';

export function deltaDirection(delta: number | null): DeltaDirection {
  if (delta === null) {
    return 'unknown';
  }
  if (delta > 0) {
    return 'up';
  }
  if (delta < 0) {
    return 'down';
  }
  return 'flat';
}

/** 大屏窄数字列的缩写，完整值仍由显示组件提供。 */
export function formatScreenCompact(value: string | number): string {
  const text = String(value);
  const parts = /^([¥￥]?)([+-]?\d[\d,]*(?:\.\d+)?)$/.exec(text);
  if (!parts) return text;
  const number = new Decimal(parts[2].replaceAll(',', ''));
  if (number.abs().lt(10_000)) return text;
  const useYi = number.abs().gte(100_000_000) || number.abs().div(10_000).toDecimalPlaces(2, Decimal.ROUND_HALF_UP).gte(10_000);
  const scaled = number.div(useYi ? 100_000_000 : 10_000).toDecimalPlaces(2, Decimal.ROUND_HALF_UP);
  return `${parts[1]}${parts[2].includes('.') ? scaled.toFixed(2) : scaled.toString()}${useYi ? '亿' : '万'}`;
}

/** 缺失数据留作图表断点，不能转换成零。 */
export function toChartValue(value: unknown): number | null {
  if (typeof value !== 'number' && (typeof value !== 'string' || !value.trim())) return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

const HTML_ENTITIES: Record<string, string> = {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'};

export function escapeScreenHtml(value: string): string {
  return value.replace(/[&<>"']/g, (char) => HTML_ENTITIES[char]);
}
