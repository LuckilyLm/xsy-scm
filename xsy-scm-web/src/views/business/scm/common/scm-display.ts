/** SCM 日期与时间统一显示为北京时间字符串，空值显示为“—”。后端已处理时区，前端不得用浏览器时区重复转换。 */

/** 规范输出形状的前 19 位：`yyyy-MM-dd HH:mm:ss`。 */
const DATETIME_CN = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})/;

/** 仅日期：`yyyy-MM-dd`。 */
const DATE_CN = /^(\d{4})-(\d{2})-(\d{2})/;

/**
 * 时间渲染：统一输出 `yyyy-MM-dd HH:mm:ss`。
 *
 * @param value 后端返回的时间串（规范 ISO-8601 或已格式化串）
 *
 * @example
 * datetime('2026-09-17T13:50:57.394795+08:00') // '2026-09-17 13:50:57'
 * datetime(null)                               // '—'
 */
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

/**
 * 日期渲染：统一输出 `yyyy-MM-dd`（用于「计划到货日期」这类纯日期字段）。
 *
 * 与 datetime 分开，是因为纯日期字段后端是 `LocalDate`，
 * 不该被硬塞一个 `00:00:00`。
 */
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
