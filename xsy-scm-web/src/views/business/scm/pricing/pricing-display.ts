import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {datetime} from '../common/scm-display';

/**
 * 价格列表的生效状态是**前端推导**的：`AgreementPriceVO` / `CustomerTypePriceVO`
 * 只返回生效区间，不带状态字段。推导结果仅用于列表展示，取价仍以服务端解析为准。
 */
export type PriceEffectiveness = 'PENDING' | 'EFFECTIVE' | 'EXPIRED';

export const PRICE_EFFECTIVENESS: Record<PriceEffectiveness, { label: string; tone: ScmStatusTone }> = {
    PENDING: {label: '未生效', tone: 'warning'},
    EFFECTIVE: {label: '生效中', tone: 'success'},
    EXPIRED: {label: '已失效', tone: 'neutral'},
};

/**
 * 当前时点，按后端的展示口径（Asia/Shanghai、`yyyy-MM-dd HH:mm:ss`）给出。
 *
 * 不解析后端返回的时间串：那会把已定好时区的字面量按浏览器本地时区二次换算。
 * 两边同格式且定宽，直接按字典序比较即可。
 */
function nowInShanghai(): string {
    const parts = new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Asia/Shanghai',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hourCycle: 'h23',
    }).formatToParts(new Date());
    const at = (type: string) => parts.find((part) => part.type === type)?.value ?? '';
    return `${at('year')}-${at('month')}-${at('day')} ${at('hour')}:${at('minute')}:${at('second')}`;
}

/** 开始时间包含、结束时间不包含；结束为空表示长期有效。 */
export function priceEffectiveness(
    effectiveFrom?: string | null,
    effectiveTo?: string | null,
    now: string = nowInShanghai()
): PriceEffectiveness {
    const from = datetime(effectiveFrom);
    const to = datetime(effectiveTo);
    if (from !== '—' && from > now) return 'PENDING';
    if (to !== '—' && to <= now) return 'EXPIRED';
    return 'EFFECTIVE';
}

/** 有效期单元主行：有结束时间给完整区间，无结束时间只给起始，第二行由调用方补「长期有效」。 */
export function effectiveRangeText(effectiveFrom?: string | null, effectiveTo?: string | null): string {
    return effectiveTo ? `${datetime(effectiveFrom)} ～ ${datetime(effectiveTo)}` : datetime(effectiveFrom);
}
