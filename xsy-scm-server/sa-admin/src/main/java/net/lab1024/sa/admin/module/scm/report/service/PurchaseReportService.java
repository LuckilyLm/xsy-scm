package net.lab1024.sa.admin.module.scm.report.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.report.dao.ReportDao;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmPurchaseReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.vo.PurchaseReportVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.SalesReportVO;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportTimeRange;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportTimeRangeResolver;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

/**
 * 采购分析（只读）。业务日期是 {@code submitted_at}，状态只认提交后的四个值。
 *
 * <p>本域没有「应付金额」：采购单金额是承诺、收货参考金额是履约事实，两者都不是应付；
 * 应付需要收货/入账时点与核销规则，R0 没有这些事实。
 *
 * <p><b>整页按仓库授权范围取数</b>：采购单、收货单与入库流水三张事实表都有 {@code warehouse_id}，
 * 所以每条语句的每个派生表都带范围谓词（见 ReportDao.xml 纪律 6），调用方的 {@code warehouseId}
 * 筛选只能在授权范围内进一步收窄。范围为空即返回空结果，而不是跑一次恒假查询换回一堆 0。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseReportService {

    /** 供应商 TOP 榜取前 10，与参考页的 TOP10 习惯一致。 */
    private static final int SUPPLIER_TOP_LIMIT = 10;

    private final ReportDao reportDao;

    private final ScmDataScopeService dataScopeService;

    public PurchaseReportVO.Overview overview(ScmPurchaseReportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            // 概览四个指标全部来自采购/收货/库存事实，无授权仓库时没有一个指标可知：
            // 返回空对象而不是 0，否则页面会显示「本期采购 0 元」。
            return new PurchaseReportVO.Overview();
        }
        PurchaseReportVO.Overview vo = reportDao.purchaseOverview(range.startAt(), range.endAt(), form,
                context.getWarehouseScope());
        if (vo != null && !context.isCostVisible()) {
            vo.setPurchaseInCostAmount(null);
            vo.setPurchaseInCostMissingCount(null);
        }
        return vo;
    }

    public PageResult<PurchaseReportVO.ProductRow> byProduct(ScmPurchaseReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        PageResult<PurchaseReportVO.ProductRow> result = SmartPageUtil.convert2PageResult(page,
                reportDao.purchaseByProduct(page, range.startAt(), range.endAt(), form,
                        context.getWarehouseScope()));
        maskProductCost(result, context.isCostVisible());
        return result;
    }

    public PageResult<PurchaseReportVO.SupplierRow> bySupplier(ScmPurchaseReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        PageResult<PurchaseReportVO.SupplierRow> result = SmartPageUtil.convert2PageResult(page,
                reportDao.purchaseBySupplier(page, range.startAt(), range.endAt(), form,
                        context.getWarehouseScope()));
        maskSupplierCost(result, context.isCostVisible());
        return result;
    }

    public List<SalesReportVO.TopItem> topSupplierInbound(ScmPurchaseReportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        if (!context.isCostVisible() || context.warehouseNowhere()) {
            return List.of();
        }
        return reportDao.topPurchaseSupplierInbound(range.startAt(), range.endAt(), SUPPLIER_TOP_LIMIT, form,
                context.getWarehouseScope());
    }

    public PageResult<PurchaseReportVO.PurchaserRow> byPurchaser(ScmPurchaseReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        PageResult<PurchaseReportVO.PurchaserRow> result = SmartPageUtil.convert2PageResult(page,
                reportDao.purchaseByPurchaser(page, range.startAt(), range.endAt(), form,
                        context.getWarehouseScope()));
        List<PurchaseReportVO.PurchaserRow> rows = result.getList();
        if (!context.isCostVisible()) {
            rows.forEach(row -> {
                row.setInboundCostAmount(null);
                row.setInboundCostMissingCount(null);
            });
        }
        return result;
    }

    public PageResult<PurchaseReportVO.ItemRow> itemList(ScmPurchaseReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page,
                reportDao.purchaseItemList(page, range.startAt(), range.endAt(), form,
                        context.getWarehouseScope()));
    }

    public List<PurchaseReportVO.PriceTrendPoint> priceTrend(ScmPurchaseReportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            return List.of();
        }
        return reportDao.purchasePriceTrend(range.startAt(), range.endAt(), form, context.getWarehouseScope());
    }

    private void maskProductCost(PageResult<PurchaseReportVO.ProductRow> result, boolean costVisible) {
        if (costVisible || result == null || result.getList() == null) {
            return;
        }
        result.getList().forEach(row -> {
            row.setInboundCostAmount(null);
            row.setInboundCostMissingCount(null);
            row.setInboundQuantityText(null);
        });
    }

    private void maskSupplierCost(PageResult<PurchaseReportVO.SupplierRow> result, boolean costVisible) {
        if (costVisible || result == null || result.getList() == null) {
            return;
        }
        result.getList().forEach(row -> {
            row.setInboundCostAmount(null);
            row.setInboundCostMissingCount(null);
        });
    }
}
