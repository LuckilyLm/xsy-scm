/**
 * 大屏接口返回类型。
 *
 * <p><b>所有小数都是 string，不是 number</b>：后端 BigDecimal 被 Jackson 序列化成字符串
 * （保留 NUMERIC(18,4) 的精度，避免 JS 的 IEEE754 把 0.1+0.2 这类金额算歪）。
 * 需要参与计算时用 {@link toNumber} 显式转换，不要在模板里直接做算术。
 */

/** 排行榜项（客户 / 商品共用）。 */
export interface RankItem {
  name: string;
  amount: string;
}

export interface BusinessData {
  todayOrderCount: number;
  todayOrderedAmount: string;
  todaySettlementAmount: string;
  totalOrderCount: number;
  totalSettlementAmount: string;
  customerCount: number;
  supplierCount: number;
  skuCount: number;
  /** 今日成交客户数（今日有 CONFIRMED 订单的客户去重） */
  todayCustomerCount: number;
  /** 今日活跃供应商数 */
  todaySupplierCount: number;
  topCustomers: RankItem[];
  topProducts: RankItem[];
}

export interface WarehouseDistribution {
  warehouseName: string;
  quantity: string;
}

/** 供应链网络节点（启用仓库）。 */
export interface WarehouseNode {
  warehouseName: string;
  quantity: string;
  todayOutboundQuantity: string;
}

/**
 * 库存健康度。
 *
 * <p>四档**互斥**，且 {@link outOfStockCount} + {@link lowCount} + {@link highCount}
 * + {@link normalCount} + {@link unconfiguredCount} === {@link totalSkuCount}，
 * 所以占比可以直接拿总数当分母。
 *
 * <p>判定顺序是「缺货 → 未配置 → 三档」：缺货只要求可用量 ≤ 0，不要求配了阈值；
 * 未配置的塞进「正常」会让健康度虚高，所以单独成档。
 */
export interface InventoryHealth {
  totalSkuCount: number;
  normalCount: number;
  lowCount: number;
  highCount: number;
  outOfStockCount: number;
  unconfiguredCount: number;
}

export interface InventoryData {
  totalQuantity: string;
  skuCount: number;
  warehouseCount: number;
  todayInboundCount: number;
  todayOutboundCount: number;
  warehouseDistribution: WarehouseDistribution[];
  health: InventoryHealth | null;
  warehouseNodes: WarehouseNode[];
}

export interface PurchaseData {
  todayPurchaseOrderCount: number;
  todayPurchaseAmount: string;
  totalPurchaseOrderCount: number;
  totalPurchaseAmount: string;
  todayReceiptCount: number;
}

/** 趋势区间：近 7 天 / 近 30 天。 */
export type ScreenRange = '7d' | '30d';

/** 趋势数据：8 条等长序列，共用同一个日期轴。 */
export interface TrendData {
  range: ScreenRange;
  /** MM-DD */
  dates: string[];
  /** YYYY-MM-DD */
  fullDates: string[];
  sales: string[];
  orders: number[];
  purchaseAmounts: string[];
  purchaseOrders: number[];
  /** 每日期末库存量（累计净额），不是当日变动量 */
  inventoryQuantity: string[];
  inboundQuantity: string[];
  outboundQuantity: string[];
}

export function emptyTrend(range: ScreenRange = '7d'): TrendData {
  return {
    range,
    dates: [],
    fullDates: [],
    sales: [],
    orders: [],
    purchaseAmounts: [],
    purchaseOrders: [],
    inventoryQuantity: [],
    inboundQuantity: [],
    outboundQuantity: [],
  };
}
