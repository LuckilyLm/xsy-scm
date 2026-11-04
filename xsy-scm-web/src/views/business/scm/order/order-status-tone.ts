import type {ScmStatusTone} from '/@/theme/scm/scm-status';

/** 待确认=待处理，确认/发车=处理中，签收=完成，异常=错误，取消=失效（订单列表、客户详情、订单详情共用同一套档位）。 */
const ORDER_STATUS_TONE: Record<string, ScmStatusTone> = {
  PENDING: 'warning',
  CONFIRMED: 'processing',
  CANCELLED: 'neutral',
  DISPATCHED: 'processing',
  SIGNED: 'success',
  EXCEPTION: 'error',
};

export const orderStatusTone = (value?: string | null): ScmStatusTone => ORDER_STATUS_TONE[value ?? ''] ?? 'neutral';
