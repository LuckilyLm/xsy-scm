export type Id = string | number;

export interface Item {
    itemId?: Id;
    version?: number;
    skuId?: Id;
    orderedQuantity: string;
    manualPriceOverride: boolean;
    unitPrice?: string | null;
    overrideReason?: string | null;
    sortOrder?: number;
    productNameSnapshot?: string;
    specNameSnapshot?: string;
    skuCodeSnapshot?: string;
    saleUnitSnapshot?: string;
    productTypeSnapshot?: string;
    draftUnitPrice?: string | null;
    draftPriceSource?: string | null;
    manualPriceReason?: string | null;
    lockedUnitPrice?: string | null;
    lockedPriceSource?: string | null;
    orderedLineAmount?: string | null;
    actualQuantity?: string | null;
    settlementLineAmount?: string | null
}

export interface Address {
    receiverName: string;
    receiverPhone: string;
    address: string
}

export interface Order {
    orderId?: Id;
    orderNo?: string;
    version?: number;
    customerId?: Id;
    customerNameSnapshot?: string;
    customerCodeSnapshot?: string;
    settleModeSnapshot?: string;
    sellerId?: Id | null;
    orderSource: string;
    status?: string;
    originalOrderId?: Id | null;
    supplementReason?: string | null;
    expectDeliveryTime?: string | null;
    remark?: string | null;
    orderedTotalAmount?: string | null;
    settlementTotalAmount?: string | null;
    createdAt?: string;
    items: Item[];
    address: Address
}

export interface Query {
    pageNum: number;
    pageSize: number;
    keyword?: string;
    status?: string;
    orderSource?: string;
    customerId?: Id;
    orderId?: Id;
    operationType?: string
}

export interface ReturnItem {
    returnItemId?: Id;
    orderItemId: Id;
    requestedQuantity: string;
    approvedQuantity?: string | null;
    lockedUnitPrice?: string;
    approvedAmount?: string
}

export interface ReturnRow {
    returnId: Id;
    orderId: Id;
    returnNo: string;
    status: string;
    version: number;
    reason: string;
    decisionReason?: string;
    approvedAmount: string;
    items: ReturnItem[]
}

export interface RefundRow {
    refundId: Id;
    orderId: Id;
    refundNo: string;
    returnId: Id;
    status: string;
    version: number;
    refundAmount: string;
    externalReference?: string | null
}

export interface LogRow {
    logId: Id;
    orderId: Id;
    operationType: string;
    operator: string;
    operatorName: string;
    reason?: string;
    beforeData: unknown;
    afterData: unknown;
    createdAt: string
}

export interface ImportError {
    rowNumber: number;
    orderKey?: string;
    column: string;
    code: string;
    message: string
}

export interface ImportResult {
    totalRows: number;
    totalOrders: number;
    confirmedOrders: number;
    pendingOrders: number;
    totalErrors: number;
    errors: ImportError[];
    orders: Order[]
}
