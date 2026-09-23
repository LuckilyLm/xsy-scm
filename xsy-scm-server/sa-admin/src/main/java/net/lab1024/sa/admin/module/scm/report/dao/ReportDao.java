package net.lab1024.sa.admin.module.scm.report.dao;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

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
 */
@Mapper
public interface ReportDao {

    // ---------- 经营概览 ----------

    ReportOverviewVO overviewKpi(@Param("startAt") OffsetDateTime startAt,
                                @Param("endAt") OffsetDateTime endAt,
                                @Param("query") ScmOverviewReportQueryForm query);

    List<ReportDailyStatVO> dailyStat(@Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       @Param("startAt") OffsetDateTime startAt,
                                       @Param("endAt") OffsetDateTime endAt,
                                       @Param("query") ScmOverviewReportQueryForm query);

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
                                              @Param("query") ScmPurchaseReportQueryForm query);

    List<PurchaseReportVO.ProductRow> purchaseByProduct(Page<?> page,
                                                       @Param("startAt") OffsetDateTime startAt,
                                                       @Param("endAt") OffsetDateTime endAt,
                                                       @Param("query") ScmPurchaseReportQueryForm query);

    List<PurchaseReportVO.SupplierRow> purchaseBySupplier(Page<?> page,
                                                         @Param("startAt") OffsetDateTime startAt,
                                                         @Param("endAt") OffsetDateTime endAt,
                                                         @Param("query") ScmPurchaseReportQueryForm query);

    List<SalesReportVO.TopItem> topPurchaseSupplierInbound(@Param("startAt") OffsetDateTime startAt,
                                                             @Param("endAt") OffsetDateTime endAt,
                                                             @Param("limit") int limit,
                                                             @Param("query") ScmPurchaseReportQueryForm query);

    List<PurchaseReportVO.PurchaserRow> purchaseByPurchaser(Page<?> page,
                                                      @Param("startAt") OffsetDateTime startAt,
                                                           @Param("endAt") OffsetDateTime endAt,
                                                           @Param("query") ScmPurchaseReportQueryForm query);

    List<PurchaseReportVO.ItemRow> purchaseItemList(Page<?> page,
                                                   @Param("startAt") OffsetDateTime startAt,
                                                   @Param("endAt") OffsetDateTime endAt,
                                                   @Param("query") ScmPurchaseReportQueryForm query);

    List<PurchaseReportVO.PriceTrendPoint> purchasePriceTrend(@Param("startAt") OffsetDateTime startAt,
                                                             @Param("endAt") OffsetDateTime endAt,
                                                             @Param("query") ScmPurchaseReportQueryForm query);

    // ---------- 收货 / 入库 ----------

    List<ReceiptReportVO.ReceiptRow> receiptItemList(Page<?> page,
                                                    @Param("startAt") OffsetDateTime startAt,
                                                    @Param("endAt") OffsetDateTime endAt,
                                                    @Param("query") ScmReceiptReportQueryForm query);

    List<ReceiptReportVO.InboundRow> inboundList(Page<?> page,
                                                 @Param("startAt") OffsetDateTime startAt,
                                                 @Param("endAt") OffsetDateTime endAt,
                                                 @Param("query") ScmReceiptReportQueryForm query);

    List<ReceiptReportVO.PendingPutawayRow> pendingPutawayList(Page<?> page,
                                                         @Param("query") ScmReceiptReportQueryForm query);

    // ---------- 库存分析 ----------

    List<InventoryReportVO.MovementRow> movementList(Page<?> page,
                                                     @Param("startAt") OffsetDateTime startAt,
                                                     @Param("endAt") OffsetDateTime endAt,
                                                     @Param("inboundTypes") List<String> inboundTypes,
                                                     @Param("query") ScmInventoryReportQueryForm query);

    InventoryReportVO.LossSummary lossSummary(@Param("startAt") OffsetDateTime startAt,
                                              @Param("endAt") OffsetDateTime endAt,
                                              @Param("query") ScmInventoryReportQueryForm query);

    List<InventoryReportVO.LossRow> lossList(Page<?> page,
                                             @Param("startAt") OffsetDateTime startAt,
                                             @Param("endAt") OffsetDateTime endAt,
                                             @Param("query") ScmInventoryReportQueryForm query);

    List<InventoryReportVO.ValueRow> inventoryValueList(Page<?> page,
                                                        @Param("query") ScmInventoryReportQueryForm query);

    List<InventoryReportVO.FlowSummaryRow> flowSummary(Page<?> page,
                                                       @Param("startAt") OffsetDateTime startAt,
                                                       @Param("endAt") OffsetDateTime endAt,
                                                       @Param("inboundTypes") List<String> inboundTypes,
                                                       @Param("query") ScmInventoryReportQueryForm query);
}
