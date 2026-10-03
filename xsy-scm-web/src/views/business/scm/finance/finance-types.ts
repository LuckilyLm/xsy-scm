import type {ScmId, ScmPage, ScmResponse} from '/@/types/business/scm/customer';

export type FinanceId = ScmId;
export type FinanceEntryType = 'NORMAL' | 'RED' | 'REVERSE';
export type FinancePaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'OTHER';
export type FinanceBusinessType = 'RECEIVABLE' | 'PAYABLE' | 'RECEIPT' | 'PAYMENT' | 'WRITE_OFF';

export interface FinanceCandidate {
    id: FinanceId;
    documentNo: string;
    partyName: string;
    linkedDocumentNo?: string;
    availableAmount: string;
    entryType: string;
}

export interface FinancePageQuery {
    pageNum: number;
    pageSize: number;
    startDate: string;
    endDate: string;
}

export interface ReceivableQuery extends FinancePageQuery {
    receivableId?: FinanceId;
    customerId?: FinanceId;
    orderNo?: string;
    entryType?: FinanceEntryType;
    settleState?: 'OPEN' | 'PARTIAL' | 'SETTLED';
}

export interface PayableQuery extends FinancePageQuery {
    payableId?: FinanceId;
    supplierId?: FinanceId;
    supplierName?: string;
    purchaseOrderNo?: string;
    entryType?: FinanceEntryType;
    settleState?: 'OPEN' | 'PARTIAL' | 'SETTLED';
}

export interface ReceiptQuery extends FinancePageQuery {
    receiptId?: FinanceId;
    customerId?: FinanceId;
    method?: FinancePaymentMethod;
    entryType?: FinanceEntryType;
    pendingOnly?: boolean;
}

export interface PaymentQuery extends FinancePageQuery {
    paymentId?: FinanceId;
    counterpartyType?: 'CUSTOMER' | 'SUPPLIER';
    counterpartyId?: FinanceId;
    counterpartyName?: string;
    method?: FinancePaymentMethod;
    sourceType?: 'ORDER_REFUND';
    entryType?: FinanceEntryType;
    pendingOnly?: boolean;
}

export interface FinanceRefundOptionQuery {
    pageNum: number;
    pageSize: number;
    keyword?: string;
}

export interface FinanceRefundOption {
    refundId: FinanceId;
    refundNo: string;
    returnNo?: string | null;
    orderNo?: string | null;
    customerId: FinanceId;
    customerName: string;
    settlementCustomerId?: FinanceId;
    settlementCustomerName?: string;
    refundAmount: string;
    completedAt: string;
}

export interface WriteOffQuery extends FinancePageQuery {
    writeOffId?: FinanceId;
    sourceId?: FinanceId;
    targetId?: FinanceId;
    sourceNo?: string;
    targetNo?: string;
    entryType?: FinanceEntryType;
    sourceType?: 'RECEIPT' | 'PAYMENT' | 'BALANCE_MOVEMENT';
}

export interface FinanceReceivable {
    receivableId: FinanceId;
    receivableNo: string;
    orderId: FinanceId;
    orderNo: string;
    customerId: FinanceId;
    customerName: string;
    settlementCustomerId?: FinanceId;
    settlementCustomerName?: string;
    sourceType: string;
    sourceId: FinanceId;
    entryType: FinanceEntryType;
    originalReceivableId?: FinanceId | null;
    originalReceivableNo?: string | null;
    amount: string;
    netAmount: string;
    writtenOffAmount: string;
    openAmount: string;
    overAppliedAmount: string;
    settleState: string;
    eventAt: string;
    dueDate?: string | null;
    reason?: string | null;
}

export interface FinancePayable {
    payableId: FinanceId;
    payableNo: string;
    purchaseOrderId: FinanceId;
    purchaseOrderNo: string;
    supplierId: FinanceId;
    supplierName: string;
    sourceType: string;
    sourceId?: FinanceId | null;
    entryType: FinanceEntryType;
    originalPayableId?: FinanceId | null;
    originalPayableNo?: string | null;
    amount: string;
    netAmount: string;
    writtenOffAmount: string;
    openAmount: string;
    overAppliedAmount: string;
    settleState: string;
    eventAt: string;
    dueDate?: string | null;
    reason?: string | null;
}

export interface FinanceReceipt {
    walletFunding?: boolean;
    receiptId: FinanceId;
    receiptNo: string;
    customerId: FinanceId;
    customerName: string;
    settlementCustomerId?: FinanceId;
    settlementCustomerName?: string;
    amount: string;
    effectiveAmount: string;
    usedAmount: string;
    pendingWriteOffAmount: string;
    method: FinancePaymentMethod;
    entryType: FinanceEntryType;
    reverseOfId?: FinanceId | null;
    reverseOfNo?: string | null;
    reason?: string | null;
    receivedAt: string;
    externalReference?: string | null;
    remark?: string | null;
}

export interface FinancePayment {
    paymentId: FinanceId;
    paymentNo: string;
    counterpartyType: 'CUSTOMER' | 'SUPPLIER';
    counterpartyId: FinanceId;
    counterpartyName: string;
    amount: string;
    effectiveAmount: string;
    usedAmount: string;
    pendingWriteOffAmount: string;
    method: FinancePaymentMethod;
    entryType: FinanceEntryType;
    reverseOfId?: FinanceId | null;
    reverseOfNo?: string | null;
    reason?: string | null;
    paidAt: string;
    externalReference?: string | null;
    sourceType?: string | null;
    sourceId?: FinanceId | null;
    remark?: string | null;
}

export interface FinanceWriteOff {
    writeOffId: FinanceId;
    writeOffNo: string;
    sourceType: 'RECEIPT' | 'PAYMENT' | 'BALANCE_MOVEMENT';
    sourceId: FinanceId;
    sourceNo: string;
    sourceName: string;
    targetType: 'RECEIVABLE' | 'PAYABLE';
    targetId: FinanceId;
    targetNo: string;
    targetName: string;
    amount: string;
    entryType: FinanceEntryType;
    reverseOfId?: FinanceId | null;
    reason?: string | null;
    writtenOffAt: string;
    operator: string;
}

export interface FinanceReceivableItem {
    receivableItemId: FinanceId;
    sourceType: string;
    sourceId: FinanceId;
    orderItemId: FinanceId;
    skuId: FinanceId;
    skuName: string;
    unit: string;
    quantity: string;
    unitPrice: string;
    /** 行毛额（未扣订单优惠）。 */
    grossAmount?: string | null;
    /** 该行承担的订单优惠分摊（正常为减免额；红字为反向减免额）。 */
    discountAmount?: string | null;
    /** 行净额 = 毛额 − 优惠分摊。 */
    amount: string;
}

export interface FinancePayableItem {
    payableItemId: FinanceId;
    sourceType: string;
    sourceId?: FinanceId | null;
    purchaseOrderItemId: FinanceId;
    skuId: FinanceId;
    skuName: string;
    unit: string;
    quantity: string;
    unitPrice: string;
    amount: string;
}

export interface FinanceOperationLog {
    id: FinanceId;
    businessType: FinanceBusinessType;
    businessId: FinanceId;
    operationType: string;
    operator: string;
    reason?: string | null;
    beforeData?: Record<string, unknown> | null;
    afterData?: Record<string, unknown> | null;
    createdAt: string;
}

export interface FinanceReceivableDetail {
    receivable: FinanceReceivable;
    items: FinanceReceivableItem[];
    redEntries: FinanceReceivable[];
    originalReceivable?: FinanceReceivable | null;
    writeOffs: FinanceWriteOff[];
    operationLogs: FinanceOperationLog[];
}

export interface FinancePayableDetail {
    payable: FinancePayable;
    items: FinancePayableItem[];
    redEntries: FinancePayable[];
    originalPayable?: FinancePayable | null;
    writeOffs: FinanceWriteOff[];
    operationLogs: FinanceOperationLog[];
}

export interface FinanceReceiptDetail {
    receipt: FinanceReceipt;
    original?: FinanceReceipt | null;
    reversal?: FinanceReceipt | null;
    writeOffs: FinanceWriteOff[];
    operationLogs: FinanceOperationLog[];
}

export interface FinancePaymentDetail {
    payment: FinancePayment;
    original?: FinancePayment | null;
    reversal?: FinancePayment | null;
    writeOffs: FinanceWriteOff[];
    operationLogs: FinanceOperationLog[];
}

export interface FinanceWriteOffAddItem {
    targetId: FinanceId;
    amount: string;
}

export interface FinanceWriteOffAddForm {
    sourceType: 'RECEIPT' | 'PAYMENT';
    sourceId: FinanceId;
    items: FinanceWriteOffAddItem[];
}

export interface FinanceReceiptAddForm {
    customerId: FinanceId;
    amount: string;
    method: FinancePaymentMethod;
    receivedAt: string;
    externalReference?: string | null;
    remark?: string | null;
}

export interface FinancePaymentAddForm {
    counterpartyType: 'CUSTOMER' | 'SUPPLIER';
    counterpartyId: FinanceId;
    amount: string;
    method: FinancePaymentMethod;
    paidAt: string;
    externalReference?: string | null;
    sourceType?: 'ORDER_REFUND' | null;
    sourceId?: FinanceId | null;
    remark?: string | null;
}

export interface FinancePayableRedForm {
    originalPayableId: FinanceId;
    reason: string;
    items: Array<{
        purchaseOrderItemId: FinanceId;
        quantity: string;
        unitPrice: string;
        amount: string;
    }>;
}

export interface FinanceWriteOffReverseForm {
    writeOffId: FinanceId;
    reason: string;
}

export interface FinanceReceiptReverseForm {
    receiptId: FinanceId;
    reason: string;
}

export interface FinancePaymentReverseForm {
    paymentId: FinanceId;
    reason: string;
}

export type FinancePagedResponse<T> = Promise<ScmResponse<ScmPage<T>>>;
