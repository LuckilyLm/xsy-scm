/**
 * Finance R0 报表中心接口（新增文件）。
 *
 * 与后端 `ScmReportController`（`/scm/report`）逐端点对应，**全部只读**：
 * 这里没有任何 create / update / delete 函数，也没有任何写端点。
 * 报表是「已成立事实的另一种看法」，不是第二套账 —— 一个不存在的函数比一个
 * 会返回 405 的函数更能说明这件事。
 *
 * 三条统一约定：
 * - 查询体一律 `POST` + JSON，日期是闭区间 `startDate` / `endDate`（前端不做日界换算）；
 * - 分页响应用 `ScmPage`（`r.data.list` / `r.data.total`），非分页响应用 `ScmResponse<T>`；
 * - 导出走 `postDownload`：它已经负责创建 `<a download>` 并从 `Content-Disposition`
 *   取文件名，因此**不在前端硬编码文件名、不自己拼 Blob**。
 *   导出不带分页参数：后端强制第 1 页 + 行数上限，且超过上限时整体拒绝而不是截断。
 */
import {postDownload, postRequest} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    InventoryFlowSummaryRow,
    InventoryLossRow,
    InventoryLossSummary,
    InventoryMovementRow,
    InventoryReportQuery,
    InventoryValuePage,
    OverviewQuery,
    PurchaseItemRow,
    PurchaseOverview,
    PurchasePriceTrendPoint,
    PurchaseProductRow,
    PurchaseQuery,
    PurchasePurchaserRow,
    PurchaseSupplierRow,
    PurchaseTopItem,
    ReceiptQuery,
    ReceiptRow,
    InboundRow,
    PendingPutawayRow,
    ReportDailyStat,
    ReportOverview,
    SalesCategoryRow,
    SalesCustomerRow,
    SalesItemRow,
    SalesProductRow,
    SalesQuery,
    SalesSellerRow,
    SalesTopItem,
} from '/@/views/business/scm/report/report-types';

const BASE = '/scm/report';

/** 经营概览：指标卡 / 日趋势 / 每日统计（计划 §4）。 */
export const reportOverviewApi = {
    /** 指标卡。 */
    overview: (data: OverviewQuery) =>
        postRequest(`${BASE}/overview`, data) as unknown as Promise<ScmResponse<ReportOverview>>,
    /**
     * 日趋势。返回**补齐过的完整日期轴**（无单据的天也是零值行），
     * 因此折线不会自己跳过某天。
     */
    trend: (data: OverviewQuery) =>
        postRequest(`${BASE}/overview/trend`, data) as unknown as Promise<ScmResponse<ReportDailyStat[]>>,
    /**
     * 每日统计。后端返回**整段数组**而不是分页结果：行数被 366 天的跨度上限天然约束住，
     * 再套一层分页只会让「这一页的合计」被误读成「整段的合计」。
     */
    daily: (data: OverviewQuery) =>
        postRequest(`${BASE}/overview/daily`, data) as unknown as Promise<ScmResponse<ReportDailyStat[]>>,
};

/** 销售分析：按商品 / 按分类 / 按客户 / 按销售员 / 订单明细（计划 §5–§10）。 */
export const reportSalesApi = {
    product: (data: SalesQuery) =>
        postRequest(`${BASE}/sales/product`, data) as unknown as Promise<ScmResponse<ScmPage<SalesProductRow>>>,
    category: (data: SalesQuery) =>
        postRequest(`${BASE}/sales/category`, data) as unknown as Promise<ScmResponse<ScmPage<SalesCategoryRow>>>,
    customer: (data: SalesQuery) =>
        postRequest(`${BASE}/sales/customer`, data) as unknown as Promise<ScmResponse<ScmPage<SalesCustomerRow>>>,
    seller: (data: SalesQuery) =>
        postRequest(`${BASE}/sales/seller`, data) as unknown as Promise<ScmResponse<ScmPage<SalesSellerRow>>>,
    item: (data: SalesQuery) =>
        postRequest(`${BASE}/sales/item/query`, data) as unknown as Promise<ScmResponse<ScmPage<SalesItemRow>>>,

    /** TOP5 由 DB 直接 `LIMIT`，不在 Java 侧排序，也不取全量回内存。 */
    productTop: (data: SalesQuery) =>
        postRequest(`${BASE}/sales/product/top`, data) as unknown as Promise<ScmResponse<SalesTopItem[]>>,
    categoryTop: (data: SalesQuery) =>
        postRequest(`${BASE}/sales/category/top`, data) as unknown as Promise<ScmResponse<SalesTopItem[]>>,
    customerTop: (data: SalesQuery) =>
        postRequest(`${BASE}/sales/customer/top`, data) as unknown as Promise<ScmResponse<SalesTopItem[]>>,

    productExport: (data: Partial<SalesQuery>) => postDownload(`${BASE}/sales/product/export`, data),
    customerExport: (data: Partial<SalesQuery>) => postDownload(`${BASE}/sales/customer/export`, data),
    itemExport: (data: Partial<SalesQuery>) => postDownload(`${BASE}/sales/item/export`, data),
};

/** 采购分析：采购概览 / 按商品 / 按供应商 / 按采购员 / 采购明细 / 价格波动（计划 §11–§16、§26）。 */
export const reportPurchaseApi = {
    overview: (data: PurchaseQuery) =>
        postRequest(`${BASE}/purchase/overview`, data) as unknown as Promise<ScmResponse<PurchaseOverview>>,
    product: (data: PurchaseQuery) =>
        postRequest(`${BASE}/purchase/product`, data) as unknown as Promise<ScmResponse<ScmPage<PurchaseProductRow>>>,
    supplier: (data: PurchaseQuery) =>
        postRequest(`${BASE}/purchase/supplier`, data) as unknown as Promise<ScmResponse<ScmPage<PurchaseSupplierRow>>>,
    purchaser: (data: PurchaseQuery) =>
        postRequest(`${BASE}/purchase/purchaser`, data) as unknown as Promise<ScmResponse<ScmPage<PurchasePurchaserRow>>>,
    item: (data: PurchaseQuery) =>
        postRequest(`${BASE}/purchase/item/query`, data) as unknown as Promise<ScmResponse<ScmPage<PurchaseItemRow>>>,

    /** 供应商采购入库成本 TOP10（来源是 `PURCHASE_IN` 流水，不是采购单金额）。 */
    supplierTop: (data: PurchaseQuery) =>
        postRequest(`${BASE}/purchase/supplier/top`, data) as unknown as Promise<ScmResponse<PurchaseTopItem[]>>,
    /**
     * 价格波动点。粒度 = 业务日 × SKU × 采购单位，
     * **不同单位不会合并成一条线**（箱价与公斤价混画没有意义）。
     */
    priceTrend: (data: PurchaseQuery) =>
        postRequest(`${BASE}/purchase/price-trend`, data) as unknown as Promise<ScmResponse<PurchasePriceTrendPoint[]>>,

    productExport: (data: Partial<PurchaseQuery>) => postDownload(`${BASE}/purchase/product/export`, data),
    supplierExport: (data: Partial<PurchaseQuery>) => postDownload(`${BASE}/purchase/supplier/export`, data),
    itemExport: (data: Partial<PurchaseQuery>) => postDownload(`${BASE}/purchase/item/export`, data),
};

/** 收货与入库：三张表是**三种不同事实**（计划 §17–§20）。 */
export const reportReceiptApi = {
    /** 收货明细：粒度 = `purchase_receipt_item`，按 `confirmed_at`。 */
    query: (data: ReceiptQuery) =>
        postRequest(`${BASE}/receipt/query`, data) as unknown as Promise<ScmResponse<ScmPage<ReceiptRow>>>,
    /** 入库明细：粒度 = 一条 `PURCHASE_IN` 流水，按 `occurred_at`。 */
    inboundQuery: (data: ReceiptQuery) =>
        postRequest(`${BASE}/inbound/query`, data) as unknown as Promise<ScmResponse<ScmPage<InboundRow>>>,
    /** 待入库：`WAREHOUSE_CONFIRM + CONFIRMED + PENDING`。**只读**，入库动作在采购收货页。 */
    pendingPutawayQuery: (data: ReceiptQuery) =>
        postRequest(`${BASE}/pending-putaway/query`, data) as unknown as Promise<ScmResponse<ScmPage<PendingPutawayRow>>>,

    receiptExport: (data: Partial<ReceiptQuery>) => postDownload(`${BASE}/receipt/export`, data),
    inboundExport: (data: Partial<ReceiptQuery>) => postDownload(`${BASE}/inbound/export`, data),
};

/** 库存分析：库存流水 / 损耗分析 / 当前库存价值 / 收发存数量版（计划 §21–§25）。 */
export const reportInventoryApi = {
    movementQuery: (data: InventoryReportQuery) =>
        postRequest(`${BASE}/inventory/movement/query`, data) as unknown as Promise<
            ScmResponse<ScmPage<InventoryMovementRow>>
        >,
    lossSummary: (data: InventoryReportQuery) =>
        postRequest(`${BASE}/inventory/loss/summary`, data) as unknown as Promise<ScmResponse<InventoryLossSummary>>,
    lossQuery: (data: InventoryReportQuery) =>
        postRequest(`${BASE}/inventory/loss/query`, data) as unknown as Promise<ScmResponse<ScmPage<InventoryLossRow>>>,
    /** 当前库存价值：后端忽略日期区间（当前时点快照），页面也必须禁用日期筛选。 */
    valueQuery: (data: InventoryReportQuery) =>
        postRequest(`${BASE}/inventory/value/query`, data) as unknown as Promise<ScmResponse<InventoryValuePage>>,
    flowSummaryQuery: (data: InventoryReportQuery) =>
        postRequest(`${BASE}/inventory/flow-summary/query`, data) as unknown as Promise<
            ScmResponse<ScmPage<InventoryFlowSummaryRow>>
        >,

    movementExport: (data: Partial<InventoryReportQuery>) => postDownload(`${BASE}/inventory/movement/export`, data),
    lossExport: (data: Partial<InventoryReportQuery>) => postDownload(`${BASE}/inventory/loss/export`, data),
    valueExport: (data: Partial<InventoryReportQuery>) => postDownload(`${BASE}/inventory/value/export`, data),
};

export default {
    reportOverviewApi,
    reportSalesApi,
    reportPurchaseApi,
    reportReceiptApi,
    reportInventoryApi,
};
