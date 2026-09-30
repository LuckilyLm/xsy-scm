import Decimal from 'decimal.js';
import dayjs from 'dayjs';

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

export function paymentMethodText(value: string | null | undefined): string {
    return value === 'CASH' ? '现金' : value === 'BANK_TRANSFER' ? '银行转账' : value === 'OTHER' ? '其他' : value || '—';
}

export function isValidPositiveAmount(value: string | null | undefined): boolean {
    if (!value || !/^(?:0|[1-9]\d{0,13})(?:\.\d{1,4})?$/.test(value)) return false;
    try {
        return new Decimal(value).greaterThan(0);
    } catch {
        return false;
    }
}

export function trimOptional(value: string | null | undefined): string | null {
    const trimmed = value?.trim();
    return trimmed ? trimmed : null;
}
