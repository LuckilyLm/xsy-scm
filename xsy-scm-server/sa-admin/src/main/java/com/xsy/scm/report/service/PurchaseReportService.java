package com.xsy.scm.report.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.report.dao.ReportDao;
import com.xsy.scm.report.domain.form.ScmPurchaseReportQueryForm;
import com.xsy.scm.report.domain.vo.PurchaseReportVO;
import com.xsy.scm.report.domain.vo.SalesReportVO;
import com.xsy.scm.report.support.ScmReportTimeRange;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

/** 采购报表按订单、收货和金额的业务时点统计，时间筛选与汇总保持一致。 */
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
                ScmPurchaseStatusEnum.committedNames(), context.getWarehouseScope());
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
                reportDao.purchaseByProduct(page, range.startAt(), range.endAt(), form, context.getWarehouseScope()));
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
                reportDao.purchaseBySupplier(page, range.startAt(), range.endAt(), form, context.getWarehouseScope()));
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
                reportDao.purchaseByPurchaser(page, range.startAt(), range.endAt(), form, context.getWarehouseScope()));
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
                reportDao.purchaseItemList(page, range.startAt(), range.endAt(), form, context.getWarehouseScope()));
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
