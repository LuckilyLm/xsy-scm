/**
 * SCM 域的**展示格式化**公共工具（跨模块共享）。
 *
 * 为什么单独抽出来：采购 / 销售 / 客户 / 商品 / 供应商等模块各有自己的
 * `*-form-model.ts` 承载该模块的定点数纪律与表单逻辑，但**时间与日期是全域统一的展示约定**，
 * 放在任一个模块里都会导致其它模块反向依赖。因此收敛到这里。
 *
 * ## 时间显示的约定（A18 同族）
 *
 * 统一输出 `yyyy-MM-dd HH:mm:ss`：
 * - `null` / `undefined` / 空串 → `—`（沿用全域「无值显示破折号」的纪律）
 * - 规范 ISO 串 → 去掉毫秒与时区后缀，正常显示
 * - 认不出的形状 → 原样返回（不猜测性解析）
 *
 * ## 为什么不做时区换算
 *
 * 后端 `ScmOffsetDateTimeSerializer` 已把 `OffsetDateTime` 统一换算到
 * `Asia/Shanghai` 再格式化输出，前端拿到的就是"要显示的字符串"。
 * 这里**刻意不用 `new Date()` 解析**：那会把带 `+08:00` / `Z` 的串按浏览器本地时区
 * 二次换算，导致同一份数据在不同机器上显示成不同时刻（且跨时区排查极难）。
 * 直接取字面量既稳，又与后端已定好的口径一致。
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
