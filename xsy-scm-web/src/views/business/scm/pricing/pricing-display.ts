import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {datetime} from '../common/scm-display';

/** 价格列表的生效状态由前端按生效区间推导，只用于展示；实际取价以服务端为准。 */
export type PriceEffectiveness = 'PENDING' | 'EFFECTIVE' | 'EXPIRED';

export const PRICE_EFFECTIVENESS: Record<PriceEffectiveness, {label: string; tone: ScmStatusTone}> = {
  PENDING: {label: '未生效', tone: 'warning'},
  EFFECTIVE: {label: '生效中', tone: 'success'},
  EXPIRED: {label: '已失效', tone: 'neutral'},
};

/** 当前上海时间，格式与后端返回一致；两边直接按字符串比较，不做时区换算。 */
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
export function priceEffectiveness(effectiveFrom?: string | null, effectiveTo?: string | null, now: string = nowInShanghai()): PriceEffectiveness {
  const from = datetime(effectiveFrom);
  const to = datetime(effectiveTo);
  if (from !== '—' && from > now) return 'PENDING';
  if (to !== '—' && to <= now) return 'EXPIRED';
  return 'EFFECTIVE';
}

/** 有效期文本：有结束时间返回完整区间；无结束时间返回起始时点，调用方可同一行补「长期有效」。 */
export function effectiveRangeText(effectiveFrom?: string | null, effectiveTo?: string | null): string {
  return effectiveTo ? `${datetime(effectiveFrom)} ～ ${datetime(effectiveTo)}` : datetime(effectiveFrom);
}

/** 价格来源的中文标签。后端返回枚举名，这里统一翻译。 */
export const HISTORY_SOURCE_LABEL: Record<string, string> = {
  AGREEMENT: '客户协议价',
  CUSTOMER_GROUP: '客户分组价',
  CUSTOMER_TYPE: '客户类型价',
  TIME_PRICE: '时价',
};

/** 价格历史的变更类型。 */
export const HISTORY_OPERATION_LABEL: Record<string, string> = {
  CREATE: '新增',
  UPDATE: '更新',
  DELETE: '删除',
};

/** 变更类型颜色：新增绿、更新蓝、删除红。 */
export const HISTORY_OPERATION_TONE: Record<string, ScmStatusTone> = {
  CREATE: 'success',
  UPDATE: 'processing',
  DELETE: 'error',
};

/** 枚举缺项时保留原值，方便发现新增类型。 */
export function historyLabel(labels: Record<string, string>, value?: string | null): string {
  if (!value) return '—';
  return labels[value] ?? value;
}
