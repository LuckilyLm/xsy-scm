import Decimal from 'decimal.js';
import dayjs from 'dayjs';
import type {ScmStatusTone} from '/@/theme/scm/scm-status';

export function initialFinanceDateRange() {
    return {
        startDate: dayjs().subtract(30, 'day').format('YYYY-MM-DD'),
        endDate: dayjs().format('YYYY-MM-DD'),
    };
}

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
    return dayjs().format('YYYY-MM-DDTHH:mm:ssZ');
}

export function entryTypeText(value: string | null | undefined): string {
    return value === 'RED' ? '红字' : value === 'REVERSE' ? '反向' : value === 'NORMAL' ? '正常' : value || '—';
}

export function settleStateText(value: string | null | undefined): string {
    return value === 'OPEN' ? '未结清' : value === 'PARTIAL' ? '部分结清' : value === 'SETTLED' ? '已结清' : value || '—';
}

export function settleStateTone(value: string | null | undefined): ScmStatusTone {
    return value === 'SETTLED' ? 'success' : value === 'PARTIAL' ? 'processing' : value === 'OPEN' ? 'warning' : 'neutral';
}

/**
 * 金额单元样式。fact：单据自身金额，恒常规显示；balance：未核销/待核销等余额，零值弱化；
 * anomaly：超额核销/差额，非零即异常，红色加粗（页面配套定义 .money-alert）。
 */
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

/** 收款差额 = 金额 − 有效金额，即已被反向冲减的部分；红字/反向行本身没有差额语义 */
export function receiptDifference(record: {entryType: string; amount: string; effectiveAmount: string}): string | null {
    if (record.entryType !== 'NORMAL') return null;
    try {
        return new Decimal(record.amount).minus(record.effectiveAmount).toFixed(4);
    } catch {
        return null;
    }
}

export function paymentMethodText(value: string | null | undefined): string {
    return value === 'CASH' ? '现金'
        : value === 'BANK_TRANSFER' ? '银行转账'
            : value === 'ONLINE_PAYMENT' ? '在线支付'
                : value === 'OTHER' ? '其他' : value || '—';
}

export function isValidPositiveAmount(value: string | null | undefined): boolean {
    if (!value || !/^(?:0|[1-9]\d{0,13})(?:\.\d{1,4})?$/.test(value)) return false;
    try {
        return new Decimal(value).greaterThan(0);
    } catch {
        return false;
    }
}

/**
 * 金额 = 数量 × 单价（定点四位小数，不经浮点）。
 *
 * <p>用于红字明细这类「金额本应由数量与单价算出来」的场景。返回 `''` 表示还算不出来
 * （任一侧缺失或非法），调用方据此放弃这一行，而不是用 0 顶上 —— 0 是合法金额，
 * 拿它当「没填」的替身会把一行空明细提交成一条零元红字。
 */
export function lineAmount(quantity: string | null | undefined, unitPrice: string | null | undefined): string {
    if (!isValidPositiveAmount(quantity) || !isValidPositiveAmount(unitPrice)) return '';
    try {
        return new Decimal(quantity).times(new Decimal(unitPrice)).toFixed(4);
    } catch {
        return '';
    }
}

export function trimOptional(value: string | null | undefined): string | null {
    const trimmed = value?.trim();
    return trimmed ? trimmed : null;
}
