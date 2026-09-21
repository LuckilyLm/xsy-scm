/**
 * 大屏格式化工具（纯函数，无状态）。
 *
 * <p>统一放这里的原因：同一个数字会在多个面板出现（今日销售额同时出现在核心指标、
 * 今日经营、趋势 tooltip），各写一份 `toLocaleString` 迟早会出现
 * 「这里带千分位、那里不带」的不一致。
 *
 * <p>后端小数一律是 string，所以入口参数统一收 `string | number | null | undefined`。
 */

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

/**
 * 数量：千分位 + 最多两位小数，整数不带小数点。
 *
 * <p>后端数量是 NUMERIC(18,4)，直接渲染会显示成 `4051.0000`；大屏上按
 * 「能省则省」的规则展示更易读。
 */
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

/**
 * 环比：与上一期相比的百分比变化。
 *
 * <p><b>基数为 0 时返回 null，而不是 0% 或 +∞</b>：昨天没营业、今天有 1 万，
 * 说「增长 0%」是错的，说「增长 ∞%」没有意义。返回 null 让调用方显示「—」，
 * 这是唯一诚实的表达。
 */
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

/**
 * 环比的方向，决定颜色：涨/跌/持平/无基数。
 *
 * <p>注意这里**只表达方向，不表达好坏** —— 销售额涨是好事、库存积压涨是坏事，
 * 由调用方决定用哪个颜色。所以不在这里映射 @state-ok / @state-danger。
 */
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
