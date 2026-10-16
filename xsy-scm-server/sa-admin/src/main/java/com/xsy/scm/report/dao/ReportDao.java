package com.xsy.scm.report.dao;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.report.domain.form.ScmInventoryReportQueryForm;
import com.xsy.scm.report.domain.form.ScmOverviewReportQueryForm;
import com.xsy.scm.report.domain.form.ScmPurchaseReportQueryForm;
import com.xsy.scm.report.domain.form.ScmReceiptReportQueryForm;
import com.xsy.scm.report.domain.form.ScmSalesReportQueryForm;
import com.xsy.scm.report.domain.vo.InventoryReportVO;
import com.xsy.scm.report.domain.vo.PurchaseReportVO;
import com.xsy.scm.report.domain.vo.ReceiptReportVO;
import com.xsy.scm.report.domain.vo.ReportDailyStatVO;
import com.xsy.scm.report.domain.vo.ReportOverviewVO;
import com.xsy.scm.report.domain.vo.SalesReportVO;

/** 报表 DAO 只执行查询，时间区间和数据范围由服务端确定；不包含写入方法。 */
@Mapper
public interface ReportDao {

    // ---------- 经营概览 ----------

    ReportOverviewVO overviewKpi(@Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt,
            @Param("query") ScmOverviewReportQueryForm query,
            @Param("committedStatuses") List<String> committedStatuses, @Param("scope") ScmValueScope scope);

    List<ReportDailyStatVO> dailyStat(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt,
            @Param("query") ScmOverviewReportQueryForm query, @Param("scope") ScmValueScope scope);

    // ---------- 销售分析 ----------

    List<SalesReportVO.ProductRow> salesByProduct(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmSalesReportQueryForm query);

    List<SalesReportVO.TopItem> topSalesProduct(@Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("limit") int limit,
            @Param("query") ScmSalesReportQueryForm query);

    List<SalesReportVO.CategoryRow> salesByCategory(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmSalesReportQueryForm query);

    List<SalesReportVO.TopItem> topSalesCategory(@Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("limit") int limit,
            @Param("query") ScmSalesReportQueryForm query);

    List<SalesReportVO.CustomerRow> salesByCustomer(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmSalesReportQueryForm query);

    List<SalesReportVO.TopItem> topSalesCustomer(@Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("limit") int limit,
            @Param("query") ScmSalesReportQueryForm query);

    List<SalesReportVO.SellerRow> salesBySeller(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmSalesReportQueryForm query);

    List<SalesReportVO.ItemRow> salesItemList(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmSalesReportQueryForm query);

    List<SalesReportVO.OrderRow> salesOrderList(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmSalesReportQueryForm query);

    // ---------- 采购分析 ----------

    PurchaseReportVO.Overview purchaseOverview(@Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmPurchaseReportQueryForm query,
            @Param("committedStatuses") List<String> committedStatuses, @Param("scope") ScmValueScope scope);

    List<PurchaseReportVO.ProductRow> purchaseByProduct(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmPurchaseReportQueryForm query,
            @Param("scope") ScmValueScope scope);

    List<PurchaseReportVO.SupplierRow> purchaseBySupplier(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmPurchaseReportQueryForm query,
            @Param("scope") ScmValueScope scope);

    List<SalesReportVO.TopItem> topPurchaseSupplierInbound(@Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("limit") int limit,
            @Param("query") ScmPurchaseReportQueryForm query, @Param("scope") ScmValueScope scope);

    List<PurchaseReportVO.PurchaserRow> purchaseByPurchaser(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmPurchaseReportQueryForm query,
            @Param("scope") ScmValueScope scope);

    List<PurchaseReportVO.ItemRow> purchaseItemList(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmPurchaseReportQueryForm query,
            @Param("scope") ScmValueScope scope);

    List<PurchaseReportVO.PriceTrendPoint> purchasePriceTrend(@Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmPurchaseReportQueryForm query,
            @Param("scope") ScmValueScope scope);

    // ---------- 收货 / 入库 ----------

    List<ReceiptReportVO.ReceiptRow> receiptItemList(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmReceiptReportQueryForm query,
            @Param("scope") ScmValueScope scope);

    List<ReceiptReportVO.InboundRow> inboundList(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmReceiptReportQueryForm query,
            @Param("scope") ScmValueScope scope);

    List<ReceiptReportVO.PendingPutawayRow> pendingPutawayList(Page<?> page,
            @Param("query") ScmReceiptReportQueryForm query, @Param("scope") ScmValueScope scope);

    // ---------- 库存分析 ----------

    List<InventoryReportVO.MovementRow> movementList(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("inboundTypes") List<String> inboundTypes,
            @Param("query") ScmInventoryReportQueryForm query, @Param("scope") ScmValueScope scope);

    InventoryReportVO.LossSummary lossSummary(@Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmInventoryReportQueryForm query,
            @Param("scope") ScmValueScope scope);

    List<InventoryReportVO.LossRow> lossList(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("query") ScmInventoryReportQueryForm query,
            @Param("scope") ScmValueScope scope);

    List<InventoryReportVO.ValueRow> inventoryValueList(Page<?> page, @Param("query") ScmInventoryReportQueryForm query,
            @Param("scope") ScmValueScope scope);

    List<InventoryReportVO.FlowSummaryRow> flowSummary(Page<?> page, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("inboundTypes") List<String> inboundTypes,
            @Param("query") ScmInventoryReportQueryForm query, @Param("scope") ScmValueScope scope);
}
