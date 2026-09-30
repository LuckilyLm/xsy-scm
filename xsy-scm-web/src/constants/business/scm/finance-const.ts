/** Finance R1 codes mirror the backend enums and protected endpoint permissions. */
import type {SmartEnum} from '/@/types/smart-enum';

export const SCM_FINANCE_PERMISSION = {
    RECEIVABLE_QUERY: 'scm:finance:receivable:query',
    PAYABLE_QUERY: 'scm:finance:payable:query',
    RECEIPT_QUERY: 'scm:finance:receipt:query',
    PAYMENT_QUERY: 'scm:finance:payment:query',
    WRITE_OFF_QUERY: 'scm:finance:write-off:query',
    RECEIPT_ADD: 'scm:finance:receipt:add',
    PAYMENT_ADD: 'scm:finance:payment:add',
    WRITE_OFF_ADD: 'scm:finance:write-off:add',
    PAYABLE_RED: 'scm:finance:payable:red',
    WRITE_OFF_REVERSE: 'scm:finance:write-off:reverse',
    RECEIPT_REVERSE: 'scm:finance:receipt:reverse',
    PAYMENT_REVERSE: 'scm:finance:payment:reverse',
    EXPORT: 'scm:finance:export',
} as const;

export const SCM_FINANCE_ENTRY_TYPE_ENUM: SmartEnum<string> = {
    NORMAL: {value: 'NORMAL', desc: '正常'},
    RED: {value: 'RED', desc: '红字'},
    REVERSE: {value: 'REVERSE', desc: '反向'},
};

export const SCM_FINANCE_SETTLE_STATE_ENUM: SmartEnum<string> = {
    OPEN: {value: 'OPEN', desc: '未结清'},
    PARTIAL: {value: 'PARTIAL', desc: '部分结清'},
    SETTLED: {value: 'SETTLED', desc: '已结清'},
};

export const SCM_FINANCE_PAYMENT_METHOD_ENUM: SmartEnum<string> = {
    CASH: {value: 'CASH', desc: '现金'},
    BANK_TRANSFER: {value: 'BANK_TRANSFER', desc: '银行转账'},
    OTHER: {value: 'OTHER', desc: '其他'},
};

export const SCM_FINANCE_COUNTERPARTY_TYPE_ENUM: SmartEnum<string> = {
    CUSTOMER: {value: 'CUSTOMER', desc: '客户'},
    SUPPLIER: {value: 'SUPPLIER', desc: '供应商'},
};

export const SCM_FINANCE_SOURCE_TYPE_ENUM: SmartEnum<string> = {
    RECEIPT: {value: 'RECEIPT', desc: '收款'},
    PAYMENT: {value: 'PAYMENT', desc: '付款'},
};

export const SCM_FINANCE_BUSINESS_TYPE_ENUM: SmartEnum<string> = {
    RECEIVABLE: {value: 'RECEIVABLE', desc: '应收'},
    PAYABLE: {value: 'PAYABLE', desc: '应付'},
    RECEIPT: {value: 'RECEIPT', desc: '收款'},
    PAYMENT: {value: 'PAYMENT', desc: '付款'},
    WRITE_OFF: {value: 'WRITE_OFF', desc: '核销'},
};

/** Stable DOM ids for browser checks; column settings use the numeric ids below. */
export const SCM_FINANCE_DOM_TABLE_ID = {
    RECEIVABLE: 'scm-finance-receivable-table',
    PAYABLE: 'scm-finance-payable-table',
    RECEIPT: 'scm-finance-receipt-table',
    PAYMENT: 'scm-finance-payment-table',
    WRITE_OFF: 'scm-finance-write-off-table',
} as const;

export const SCM_FINANCE_ENTRY_COLOR: Record<string, string> = {
    NORMAL: 'blue',
    RED: 'red',
    REVERSE: 'orange',
};

export default {
    SCM_FINANCE_ENTRY_TYPE_ENUM,
    SCM_FINANCE_SETTLE_STATE_ENUM,
    SCM_FINANCE_PAYMENT_METHOD_ENUM,
    SCM_FINANCE_COUNTERPARTY_TYPE_ENUM,
    SCM_FINANCE_SOURCE_TYPE_ENUM,
    SCM_FINANCE_BUSINESS_TYPE_ENUM,
};
