package net.lab1024.sa.admin.module.scm.report.dao;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmInventoryReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmOverviewReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmPurchaseReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmReceiptReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmSalesReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.vo.InventoryReportVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.PurchaseReportVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReceiptReportVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReportDailyStatVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReportOverviewVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.SalesReportVO;

/**
 * Finance R0 报表只读 DAO。SQL 全部在 {@code mapper/scm/report/ReportDao.xml}。
 *
 * <p>只声明 select，不声明任何写方法；每个查询的时间参数已由
 * {@code ScmReportTimeRangeResolver} 收敛成 Asia/Shanghai 的半开区间瞬间，
 * 因此 XML 里只出现 {@code >= startAt AND < endAt} 一种日界写法。
 *
 * <p><b>{@code scope} 是调用者的仓库数据范围，必须由 Service 显式下传</b>（裁决
 * {@code docs/decisions.md}「P0 基线收口裁决」第 2、10 条）：为 {@code null} 时 XML 退化为
 * 恒假谓词，即「没有授权范围就查不到数据」，不允许用 {@code null} 表达「全部」。
 * 空授权清单（{@code ScmValueScope#none()}）会渲染成非法的 {@code IN ()}，
 * 所以调用方要么先短路成空结果，要么（仅概览两条，因为它同时带着不受仓库范围约束的销售指标）
 * 传 {@code null} 走恒假分支。
 * 页面查询与 Excel 导出共用本 DAO 的同一批方法，因此导出不会比页面看到更多仓库。
 * 销售侧语句不带 {@code scope}：{@code sales_order} / {@code order_refund} 上没有仓库列。
 */
@Mapper
public interface ReportDao {

    // ---------- 经营概览 ----------

    ReportOverviewVO overviewKpi(@Param("startAt") OffsetDateTime startAt,
                                @Param("endAt") OffsetDateTime endAt,
                                @Param("query") ScmOverviewReportQueryForm query,
                                @Param("scope") ScmValueScope scope);

    List<ReportDailyStatVO> dailyStat(@Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       @Param("startAt") OffsetDateTime startAt,
                                       @Param("endAt") OffsetDateTime endAt,
                                       @Param("query") ScmOverviewReportQueryForm query,
                                       @Param("scope") ScmValueScope scope);

    // ---------- 销售分析 ----------

    List<SalesReportVO.ProductRow> salesByProduct(Page<?> page,
                                                  @Param("startAt") OffsetDateTime startAt,
                                                  @Param("endAt") OffsetDateTime endAt,
                                                  @Param("query") ScmSalesReportQueryForm query);

    List<SalesReportVO.TopItem> topSalesProduct(@Param("startAt") OffsetDateTime startAt,
                                                @Param("endAt") OffsetDateTime endAt,
                                                @Param("limit") int limit,
                                                @Param("query") ScmSalesReportQueryForm query);

    List<SalesReportVO.CategoryRow> salesByCategory(Page<?> page,
                                                   @Param("startAt") OffsetDateTime startAt,
                                                   @Param("endAt") OffsetDateTime endAt,
                                                   @Param("query") ScmSalesReportQueryForm query);

    List<SalesReportVO.TopItem> topSalesCategory(@Param("startAt") OffsetDateTime startAt,
                                                @Param("endAt") OffsetDateTime endAt,
                                                @Param("limit") int limit,
                                                @Param("query") ScmSalesReportQueryForm query);

    List<SalesReportVO.CustomerRow> salesByCustomer(Page<?> page,
                                                   @Param("startAt") OffsetDateTime startAt,
                                                   @Param("endAt") OffsetDateTime endAt,
                                                   @Param("query") ScmSalesReportQueryForm query);

    List<SalesReportVO.TopItem> topSalesCustomer(@Param("startAt") OffsetDateTime startAt,
                                                @Param("endAt") OffsetDateTime endAt,
                                                @Param("limit") int limit,
                                                @Param("query") ScmSalesReportQueryForm query);

    List<SalesReportVO.SellerRow> salesBySeller(Page<?> page,
                                               @Param("startAt") OffsetDateTime startAt,
                                               @Param("endAt") OffsetDateTime endAt,
                                               @Param("query") ScmSalesReportQueryForm query);

    List<SalesReportVO.ItemRow> salesItemList(Page<?> page,
                                             @Param("startAt") OffsetDateTime startAt,
                                             @Param("endAt") OffsetDateTime endAt,
                                             @Param("query") ScmSalesReportQueryForm query);

    // ---------- 采购分析 ----------

    PurchaseReportVO.Overview purchaseOverview(@Param("startAt") OffsetDateTime startAt,
                                              @Param("endAt") OffsetDateTime endAt,
                                              @Param("query") ScmPurchaseReportQueryForm query,
                                              @Param("scope") ScmValueScope scope);

    List<PurchaseReportVO.ProductRow> purchaseByProduct(Page<?> page,
                                                       @Param("startAt") OffsetDateTime startAt,
                                                       @Param("endAt") OffsetDateTime endAt,
                                                       @Param("query") ScmPurchaseReportQueryForm query,
                                                       @Param("scope") ScmValueScope scope);

    List<PurchaseReportVO.SupplierRow> purchaseBySupplier(Page<?> page,
                                                         @Param("startAt") OffsetDateTime startAt,
                                                         @Param("endAt") OffsetDateTime endAt,
                                                         @Param("query") ScmPurchaseReportQueryForm query,
                                                         @Param("scope") ScmValueScope scope);

    List<SalesReportVO.TopItem> topPurchaseSupplierInbound(@Param("startAt") OffsetDateTime startAt,
                                                             @Param("endAt") OffsetDateTime endAt,
                                                             @Param("limit") int limit,
                                                             @Param("query") ScmPurchaseReportQueryForm query,
                                                             @Param("scope") ScmValueScope scope);

    List<PurchaseReportVO.PurchaserRow> purchaseByPurchaser(Page<?> page,
                                                      @Param("startAt") OffsetDateTime startAt,
                                                           @Param("endAt") OffsetDateTime endAt,
                                                           @Param("query") ScmPurchaseReportQueryForm query,
                                                           @Param("scope") ScmValueScope scope);

    List<PurchaseReportVO.ItemRow> purchaseItemList(Page<?> page,
                                                   @Param("startAt") OffsetDateTime startAt,
                                                   @Param("endAt") OffsetDateTime endAt,
                                                   @Param("query") ScmPurchaseReportQueryForm query,
                                                   @Param("scope") ScmValueScope scope);

    List<PurchaseReportVO.PriceTrendPoint> purchasePriceTrend(@Param("startAt") OffsetDateTime startAt,
                                                             @Param("endAt") OffsetDateTime endAt,
                                                             @Param("query") ScmPurchaseReportQueryForm query,
                                                             @Param("scope") ScmValueScope scope);

    // ---------- 收货 / 入库 ----------

    List<ReceiptReportVO.ReceiptRow> receiptItemList(Page<?> page,
                                                    @Param("startAt") OffsetDateTime startAt,
                                                    @Param("endAt") OffsetDateTime endAt,
                                                    @Param("query") ScmReceiptReportQueryForm query,
                                                    @Param("scope") ScmValueScope scope);

    List<ReceiptReportVO.InboundRow> inboundList(Page<?> page,
                                                 @Param("startAt") OffsetDateTime startAt,
                                                 @Param("endAt") OffsetDateTime endAt,
                                                 @Param("query") ScmReceiptReportQueryForm query,
                                                 @Param("scope") ScmValueScope scope);

    List<ReceiptReportVO.PendingPutawayRow> pendingPutawayList(Page<?> page,
                                                         @Param("query") ScmReceiptReportQueryForm query,
                                                         @Param("scope") ScmValueScope scope);

    // ---------- 库存分析 ----------

    List<InventoryReportVO.MovementRow> movementList(Page<?> page,
                                                     @Param("startAt") OffsetDateTime startAt,
                                                     @Param("endAt") OffsetDateTime endAt,
                                                     @Param("inboundTypes") List<String> inboundTypes,
                                                     @Param("query") ScmInventoryReportQueryForm query,
                                                     @Param("scope") ScmValueScope scope);

    InventoryReportVO.LossSummary lossSummary(@Param("startAt") OffsetDateTime startAt,
                                              @Param("endAt") OffsetDateTime endAt,
                                              @Param("query") ScmInventoryReportQueryForm query,
                                              @Param("scope") ScmValueScope scope);

    List<InventoryReportVO.LossRow> lossList(Page<?> page,
                                             @Param("startAt") OffsetDateTime startAt,
                                             @Param("endAt") OffsetDateTime endAt,
                                             @Param("query") ScmInventoryReportQueryForm query,
                                             @Param("scope") ScmValueScope scope);

    List<InventoryReportVO.ValueRow> inventoryValueList(Page<?> page,
                                                        @Param("query") ScmInventoryReportQueryForm query,
                                                        @Param("scope") ScmValueScope scope);

    List<InventoryReportVO.FlowSummaryRow> flowSummary(Page<?> page,
                                                       @Param("startAt") OffsetDateTime startAt,
                                                       @Param("endAt") OffsetDateTime endAt,
                                                       @Param("inboundTypes") List<String> inboundTypes,
                                                       @Param("query") ScmInventoryReportQueryForm query,
                                                       @Param("scope") ScmValueScope scope);
}
