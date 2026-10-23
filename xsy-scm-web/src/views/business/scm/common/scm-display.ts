/** 日期时间统一按北京时间显示，空值为「—」；前端不做时区转换。 */

/** 规范输出形状的前 19 位：`yyyy-MM-dd HH:mm:ss`。 */
const DATETIME_CN = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})/;

/** 仅日期：`yyyy-MM-dd`。 */
const DATE_CN = /^(\d{4})-(\d{2})-(\d{2})/;

/** 时间统一显示为 `yyyy-MM-dd HH:mm:ss`，空值显示“—”。 */
export function datetime(value: string | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const matched = DATETIME_CN.exec(value);
  if (!matched) {
    return value;
  }
  const [, y, mo, d, h, mi, s] = matched;
  return `${y}-${mo}-${d} ${h}:${mi}:${s}`;
}

/** 纯日期显示为 `yyyy-MM-dd`，不补时间。 */
export function dateOnly(value: string | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const matched = DATE_CN.exec(value);
  if (!matched) {
    return value;
  }
  const [, y, mo, d] = matched;
  return `${y}-${mo}-${d}`;
}
