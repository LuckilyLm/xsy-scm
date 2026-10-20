import type {ScmStatusTone} from '/@/theme/scm/scm-status';

/** 草稿/待确认=待处理，已确认=处理中，已取消=失效（订单列表、客户详情、订单详情共用同一套档位）。 */
const ORDER_STATUS_TONE: Record<string, ScmStatusTone> = {
  DRAFT: 'warning',
  PENDING: 'warning',
  CONFIRMED: 'processing',
  CANCELLED: 'neutral',
};

export const orderStatusTone = (value?: string | null): ScmStatusTone => ORDER_STATUS_TONE[value ?? ''] ?? 'neutral';
