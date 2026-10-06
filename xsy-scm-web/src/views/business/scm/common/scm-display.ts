/**
 * SCM 域展示格式化公共工具（跨模块共享）：时间与日期是全域统一的展示约定，
 * 放在任一业务模块里都会让其它模块反向依赖。
 *
 * 统一输出 `yyyy-MM-dd HH:mm:ss`；`null` / `undefined` / 空串 → `—`；
 * 认不出的形状原样返回（不猜测性解析）。
 *
 * <b>刻意不做时区换算</b>：后端 `ScmOffsetDateTimeSerializer` 已统一换算到 `Asia/Shanghai`
 * 再格式化，前端拿到的就是要显示的字符串。用 `new Date()` 解析会按浏览器本地时区
 * 二次换算，同一份数据在不同机器上显示成不同时刻。
 */

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
 * 与 {@link datetime} 分开，是因为纯日期字段后端是 `LocalDate`，
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
