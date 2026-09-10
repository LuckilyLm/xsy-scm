import type { PageData, ProductType, ShelfStatus } from './product';

export type CustomerStatus = 'ENABLED' | 'DISABLED';
export type VisibilityPolicy = 'ALL_ENABLED' | 'ALLOWLIST';
export type CustomerTypeStatus = CustomerStatus;
export type PriceSource = 'AGREEMENT' | 'MARKET' | 'OVERRIDE';
export type OrderStatus = 'DRAFT' | 'PENDING' | 'CONFIRMED' | 'CANCELLED';
export type OrderSource = 'NORMAL' | 'SUPPLEMENT';
export type ReturnStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
export type RefundStatus = 'PENDING' | 'COMPLETED';

export interface CustomerType { id: number; typeCode: string; name: string; status: CustomerTypeStatus; version: number; }
/** 与后端 `CustomerTypeSaveRequest` 对齐；新增时不传 version，编辑时携带当前 version。 */
export interface CustomerTypeInput { typeCode: string; name: string; status: CustomerTypeStatus; version?: number; }
export interface CustomerSummary {
  id: number; version: number; customerCode: string; name: string; customerTypeId: number;
  customerTypeName: string; status: CustomerStatus; visibilityPolicy: VisibilityPolicy; updatedAt: string;
}
export interface CustomerSkuVisibility { id?: number; version?: number; skuId: number; }
export interface CustomerDetail extends CustomerSummary { visibilities: CustomerSkuVisibility[]; }
export interface CustomerPayload {
  version: number | null; customerCode: string; name: string; customerTypeId: number;
  status: CustomerStatus; visibilityPolicy: VisibilityPolicy; visibilities: CustomerSkuVisibility[];
}
export interface AgreementPrice {
  id: number; version: number; customerId: number; skuId: number; unitPrice: string;
  effectiveFrom: string; effectiveTo: string | null;
}
export interface AgreementPricePayload {
  version: number | null; customerId: number; skuId: number; unitPrice: string;
  effectiveFrom: string; effectiveTo: string | null;
}
export interface OrderItem {
  id: number | null; version: number | null; skuId: number; productId: number;
  skuCode: string; productName: string; specName: string; specValues: Record<string, string>;
  saleUnit: string; productType: ProductType; orderedQuantity: string; actualQuantity: string | null;
  unitPrice: string; lockedUnitPrice: string | null; priceSource: PriceSource; priceSourceId: number | null;
  amount: string | null; overrideReason: string | null;
}
export interface SalesOrder {
  id: number; version: number; orderNo: string; customerId: number; customerName: string;
  status: OrderStatus; source: OrderSource; supplementReason: string | null; originalOrderId: number | null;
  items: OrderItem[]; totalAmount: string; createdAt: string; updatedAt: string;
}
export interface SalesOrderPayload {
  version: number | null; customerId: number; source: OrderSource; supplementReason: string | null;
  originalOrderId: number | null; items: SalesOrderItemPayload[];
}
export interface SalesOrderItemPayload {
  id: number | null; version: number | null; skuId: number; orderedQuantity: string;
  unitPrice: string | null; manualPriceOverride: boolean; overrideReason: string | null;
}
export interface ActualQuantityPayload { version: number; actualQuantity: string; reason: string; }
export interface CancelOrderPayload { version: number; reason: string; }
export interface ReturnItemPayload { orderItemId: number; requestedQuantity: string; }
export interface ReturnItem { id: number; orderItemId: number; requestedQuantity: string; approvedQuantity: string | null; lockedUnitPrice: string; approvedAmount: string; version: number; }
export interface OrderReturn { id: number; version: number; returnNo: string; orderId: number; customerId: number; status: ReturnStatus; reason: string; decisionReason: string | null; items: ReturnItem[]; approvedAmount: string; }
export interface OrderReturnPayload { orderId: number; reason: string; items: ReturnItemPayload[]; }
export interface ApproveReturnPayload { version: number; items: Array<{ returnItemId: number; version: number; approvedQuantity: string }>; }
export interface Refund { id: number; version: number; refundNo: string; returnId: number; orderId: number; customerId: number; status: RefundStatus; refundAmount: string; externalReference: string | null; completedAt: string | null; }
export interface CompleteRefundPayload { version: number; externalReference?: string; }
export interface OrderOperationLog { id: number; operationType: string; operator: string; beforeData: unknown; afterData: unknown; createdAt: string; }

export type CustomerPage = PageData<CustomerSummary>;
export type OrderPage = PageData<SalesOrder>;
export type ReturnPage = PageData<OrderReturn>;
export type RefundPage = PageData<Refund>;
export interface SalesPageParams { page: number; pageSize: number; keyword?: string; status?: string; customerId?: number; customerTypeId?: number; }
export type SkuSaleOption = { skuId: number; skuCode: string; productName: string; saleUnit: string; productType: ProductType; marketPrice: string; status: ShelfStatus };
