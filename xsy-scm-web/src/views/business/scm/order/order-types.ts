import type {OrderDiscount, OrderGiftEntitlement} from '/@/views/business/scm/promotion/promotion-types';

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
  settlementLineAmount?: string | null;
}

export interface Address {
  receiverName: string;
  receiverPhone: string;
  address: string;
}

export interface Order {
  orderId?: Id;
  orderNo?: string;
  version?: number;
  customerId?: Id;
  customerNameSnapshot?: string;
  customerCodeSnapshot?: string;
  settleModeSnapshot?: string;
  settlementMethod?: 'WALLET' | 'CREDIT' | 'COD' | null;
  sellerId?: Id | null;
  orderSource: string;
  mallCouponInstanceId?: Id | null;
  /** 订单选定的履约仓。 */
  deliveryWarehouseId?: Id | null;
  /** 旧接口沿用的仓库字段。 */
  mallDeliveryWarehouseId?: Id | null;
  status?: string;
  /** 最近一次发车出库时间；为空表示还没发车。 */
  dispatchedAt?: string | null;
  /** 锁单标记：锁住后改单需要独立权限，订单状态仍是已确认。 */
  lockedFlag?: boolean;
  lockedAt?: string | null;
  lockedBy?: string | null;
  lockReason?: string | null;
  autoLockAt?: string | null;
  /** 后端判定的改单资格：前端按钮只看它，不自己拼状态与权限。 */
  editable?: boolean;
  editBlockedReason?: string | null;
  originalOrderId?: Id | null;
  supplementReason?: string | null;
  expectDeliveryTime?: string | null;
  remark?: string | null;
  orderedTotalAmount?: string | null;
  settlementTotalAmount?: string | null;
  createdAt?: string;
  confirmedAt?: string | null;
  items: Item[];
  address: Address;
  tags?: import('/@/api/business/scm/order-tag-api').OrderTag[];
  /** 订单优惠；无优惠时为 null。金额列不含优惠，原价和优惠额分开显示。 */
  discount?: OrderDiscount | null;
  /** 满赠赠品；无赠品时为空。赠品不是金额，和优惠分开存。 */
  gifts?: OrderGiftEntitlement[] | null;
  inventoryFactsVisible?: boolean;
  sortingFactsVisible?: boolean;
  deliveryFactsVisible?: boolean;
  returnFactsVisible?: boolean;
  refundFactsVisible?: boolean;
  receivableFactsVisible?: boolean;
  fundingFactsVisible?: boolean;
  funding?: {
    payableAmount: string;
    receivedAmount: string;
    remainingAmount: string;
    excessReceivedAmount: string;
  } | null;
  inventoryReservations?: OrderReservation[] | null;
  sortingProgress?: OrderSorting[] | null;
  deliveryProgress?: OrderDelivery | null;
  returns?: OrderReturnFact[] | null;
  refunds?: OrderRefundFact[] | null;
}

export interface OrderReservation {
  reservationId: Id;
  orderItemId: Id;
  productName?: string | null;
  skuName?: string | null;
  warehouseName?: string | null;
  unit?: string | null;
  quantity: string;
  status: 'ACTIVE' | 'RELEASED' | 'CONSUMED';
  occurredAt?: string | null;
}

export interface OrderSorting {
  taskId: Id;
  taskNo: string;
  taskStatus: string;
  orderItemId: Id;
  productName?: string | null;
  skuName?: string | null;
  unit?: string | null;
  plannedQuantity: string;
  sortedQuantity?: string | null;
  result?: string | null;
  sortedAt?: string | null;
}

export interface OrderDelivery {
  routeId: Id;
  routeNo: string;
  routeStatus: string;
  fulfillmentStatus: string;
  warehouseName?: string | null;
  dispatchedAt?: string | null;
  signedAt?: string | null;
  signReason?: string | null;
}

export interface OrderReturnFact {
  returnId: Id;
  returnNo: string;
  status: string;
  reason: string;
  approvedAmount: string;
  approvedAt?: string | null;
  redReceivableRecorded?: boolean | null;
  redReceivableAmount?: string | null;
}

export interface OrderRefundFact {
  refundId: Id;
  returnId: Id;
  returnNo?: string | null;
  refundNo: string;
  status: 'PENDING' | 'COMPLETED';
  refundAmount: string;
  completedAt?: string | null;
  fundingStatus?: 'PENDING' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | null;
  fundingSucceededAmount?: string | null;
}

export interface Query {
  pageNum: number;
  pageSize: number;
  keyword?: string;
  status?: string;
  orderSource?: string;
  tagId?: Id;
  customerId?: Id;
  orderId?: Id;
  operationType?: string;
}

export interface OrderPoolQuery {
  pageNum: number;
  pageSize: number;
  keyword?: string;
  status?: string;
}

export interface OrderPoolRow {
  orderId: Id;
  orderNo: string;
  customerNameSnapshot: string;
  status: string;
  version: number;
  orderedTotalAmount?: string | null;
  settlementTotalAmount?: string | null;
  createdAt?: string | null;
}

export interface OrderPoolAssignee {
  employeeId: number;
  actualName: string;
}

/** 某规格最近一张已提交采购单的采购价，只读，仅用于录单时参考，不参与定价。 */
export interface LastPurchasePrice {
  skuId: Id;
  purchasePrice: string;
  /** 采购价对应的单位；与订单行销售单位不同时不可直接比较。 */
  purchaseUnit?: string | null;
  purchaseOrderNo: string;
  submittedAt: string;
  supplierName: string;
}

/** 某客户某规格最近已确认订单的成交单价，只读，仅用于录单时参考，不参与定价。 */
export interface RecentPrice {
  itemId: Id;
  orderId: Id;
  orderNo: string;
  createdAt: string;
  confirmedAt: string;
  orderSource: string;
  orderedQuantity: string;
  unitPrice: string;
  priceSource?: string | null;
  saleUnit?: string | null;
}

export interface ReturnItem {
  productName?: string;
  unit?: string;
  returnItemId?: Id;
  orderItemId: Id;
  requestedQuantity: string;
  approvedQuantity?: string | null;
  lockedUnitPrice?: string;
  approvedAmount?: string;
  receivedQuantity?: string;
}

export interface ReturnRow {
  returnId: Id;
  orderId: Id;
  orderNo?: string | null;
  customerName?: string | null;
  returnNo: string;
  status: string;
  version: number;
  reason: string;
  decisionReason?: string;
  approvedAmount: string;
  items: ReturnItem[];
}

export interface RefundRow {
  balanceMovementId?: Id | null;
  balanceReturnedAmount?: string | null;
  refundId: Id;
  orderId: Id;
  refundNo: string;
  returnId: Id;
  status: string;
  version: number;
  refundAmount: string;
  externalReference?: string | null;
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
  createdAt: string;
}

export interface ImportError {
  rowNumber: number;
  orderKey?: string;
  column: string;
  code: string;
  message: string;
}

export interface ImportResult {
  totalRows: number;
  totalOrders: number;
  confirmedOrders: number;
  pendingOrders: number;
  totalErrors: number;
  errors: ImportError[];
  orders: Order[];
}
