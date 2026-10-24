import Decimal from 'decimal.js';
import dayjs from 'dayjs';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';
import {SCM_FINANCE_OPERATION_TYPE_ENUM} from '/@/constants/business/scm/finance-const';

// 先保留完整乘积，再按金额精度舍入。
const LineAmountDecimal = Decimal.clone({precision: 40});

export function initialFinanceDateRange() {
  return {
    startDate: dayjs().subtract(30, 'day').format('YYYY-MM-DD'),
    endDate: dayjs().format('YYYY-MM-DD'),
  };
}

/** 财务账本金额固定四位：核销、退款、余额存在四位精确相等校验，不能按销售金额收敛到两位。 */
export function moneyText(value: string | null | undefined): string {
  if (value == null || value === '') return '—';
  try {
    const fixed = new Decimal(value).toFixed(4);
    const [integer, fraction] = fixed.split('.');
    return `¥ ${integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${fraction}`;
  } catch {
    return '—';
  }
}

export function numberText(value: string | null | undefined): string {
  if (value == null || value === '') return '—';
  try {
    return new Decimal(value).toFixed(4);
  } catch {
    return '—';
  }
}

export function dateTimeText(value: string | null | undefined): string {
  if (!value) return '—';
  const date = dayjs(value);
  return date.isValid() ? date.format('YYYY-MM-DD HH:mm:ss') : value;
}

export function nowDateTimeValue(): string {
  return dayjs().format('YYYY-MM-DD HH:mm:ss');
}

export function entryTypeText(value: string | null | undefined): string {
  return value === 'RED' ? '红字' : value === 'REVERSE' ? '反向' : value === 'NORMAL' ? '正常' : value || '—';
}

export function settleStateText(value: string | null | undefined): string {
  return value === 'OPEN' ? '未结清' : value === 'PARTIAL' ? '部分结清' : value === 'SETTLED' ? '已结清' : value || '—';
}

/** 操作类型转中文；不认识的保留原值。 */
export function operationTypeText(value: string | null | undefined): string {
  if (!value) return '—';
  return SCM_FINANCE_OPERATION_TYPE_ENUM[value]?.desc || value;
}

export function settleStateTone(value: string | null | undefined): ScmStatusTone {
  return value === 'SETTLED' ? 'success' : value === 'PARTIAL' ? 'processing' : value === 'OPEN' ? 'warning' : 'neutral';
}

/** 金额样式：零余额变灰，异常金额标红。 */
export function moneyClass(value: string | null | undefined, mode: 'fact' | 'balance' | 'anomaly' = 'fact'): string {
  if (value == null || value === '') return 'scm-money scm-money--muted';
  try {
    const amount = new Decimal(value);
    if (amount.isNegative()) return 'scm-money scm-money--negative';
    if (mode === 'anomaly') return amount.isZero() ? 'scm-money scm-money--muted' : 'scm-money scm-money--negative money-alert';
    if (mode === 'balance' && amount.isZero()) return 'scm-money scm-money--muted';
  } catch {
    return 'scm-money';
  }
  return 'scm-money';
}

/** 收款差额 = 金额 − 有效金额，即被反向冲减的部分；红字和反向行没有差额。 */
export function receiptDifference(record: {entryType: string; amount: string; effectiveAmount: string}): string | null {
  if (record.entryType !== 'NORMAL') return null;
  try {
    return new Decimal(record.amount).minus(record.effectiveAmount).toFixed(4);
  } catch {
    return null;
  }
}

export function paymentMethodText(value: string | null | undefined): string {
  return value === 'CASH'
    ? '现金'
    : value === 'BANK_TRANSFER'
    ? '银行转账'
    : value === 'ONLINE_PAYMENT'
    ? '在线支付'
    : value === 'OTHER'
    ? '其他'
    : value || '—';
}

export function isValidPositiveAmount(value: string | null | undefined): boolean {
  if (!value || !/^(?:0|[1-9]\d{0,13})(?:\.\d{1,4})?$/.test(value)) return false;
  try {
    return new Decimal(value).greaterThan(0);
  } catch {
    return false;
  }
}

/** 数量和单价算四位小数金额，不用浮点数；输入无效或超出范围返回空字符串。 */
export function lineAmount(quantity: string | null | undefined, unitPrice: string | null | undefined): string {
  if (quantity == null || unitPrice == null || !isValidPositiveAmount(quantity) || !isValidPositiveAmount(unitPrice)) return '';
  try {
    const amount = new LineAmountDecimal(quantity).times(unitPrice).toFixed(4, Decimal.ROUND_HALF_UP);
    return isValidPositiveAmount(amount) ? amount : '';
  } catch {
    return '';
  }
}

export function trimOptional(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}
