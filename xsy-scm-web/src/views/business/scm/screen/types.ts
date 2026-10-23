/** 金额和数量在接口中使用定点数字符串，计算时显式转换，不直接在模板里做浮点运算。 */

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

/** 库存健康度：五档互斥，合计等于总规格数；未配置阈值单独成档。 */
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

/** 地理分布：坐标用区划质心（GCJ-02），不是单位实际地址。 */
export interface GeoCityNode {
  cityCode: number;
  cityName: string;
  provinceCode: number;
  provinceName: string;
  /** 质心经度，后端 BigDecimal → JSON 字符串，喂给 ECharts 前必须 toNumber */
  centerLng: string;
  centerLat: string;
  customerCount: number;
  supplierCount: number;
  warehouseCount: number;
}

export interface GeoProvinceNode {
  provinceCode: number;
  provinceName: string;
  cityCount: number;
  customerCount: number;
  supplierCount: number;
  warehouseCount: number;
}

/** 归属覆盖度：*Total 是全部，*Located 是能定位到市的；差额要显示在大屏上。 */
export interface GeoCoverage {
  customerTotal: number;
  customerLocated: number;
  supplierTotal: number;
  supplierLocated: number;
  warehouseTotal: number;
  warehouseLocated: number;
}

export interface GeoData {
  cities: GeoCityNode[];
  provinces: GeoProvinceNode[];
  coverage: GeoCoverage | null;
}

export function emptyGeo(): GeoData {
  return {cities: [], provinces: [], coverage: null};
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
