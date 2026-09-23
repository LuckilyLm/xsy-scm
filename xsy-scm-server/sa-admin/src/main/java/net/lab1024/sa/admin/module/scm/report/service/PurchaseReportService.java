package net.lab1024.sa.admin.module.scm.report.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.report.dao.ReportDao;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmPurchaseReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.vo.PurchaseReportVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.SalesReportVO;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportAccess;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportTimeRange;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportTimeRangeResolver;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

/**
 * 采购分析（只读）。业务日期是 {@code submitted_at}，状态只认提交后的四个值。
 *
 * <p>本域没有「应付金额」：采购单金额是承诺、收货参考金额是履约事实，两者都不是应付；
 * 应付需要收货/入账时点与核销规则，R0 没有这些事实。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseReportService {

    /** 供应商 TOP 榜取前 10，与参考页的 TOP10 习惯一致。 */
    private static final int SUPPLIER_TOP_LIMIT = 10;

    private final ReportDao reportDao;

    public PurchaseReportVO.Overview overview(ScmPurchaseReportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        PurchaseReportVO.Overview vo = reportDao.purchaseOverview(range.startAt(), range.endAt(), form);
        if (vo != null && !ScmReportAccess.canViewCost()) {
            vo.setPurchaseInCostAmount(null);
            vo.setPurchaseInCostMissingCount(null);
        }
        return vo;
    }

    public PageResult<PurchaseReportVO.ProductRow> byProduct(ScmPurchaseReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        PageResult<PurchaseReportVO.ProductRow> result = SmartPageUtil.convert2PageResult(page,
                reportDao.purchaseByProduct(page, range.startAt(), range.endAt(), form));
        maskProductCost(result);
        return result;
    }

    public PageResult<PurchaseReportVO.SupplierRow> bySupplier(ScmPurchaseReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        PageResult<PurchaseReportVO.SupplierRow> result = SmartPageUtil.convert2PageResult(page,
                reportDao.purchaseBySupplier(page, range.startAt(), range.endAt(), form));
        maskSupplierCost(result);
        return result;
    }

    public List<SalesReportVO.TopItem> topSupplierInbound(ScmPurchaseReportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        if (!ScmReportAccess.canViewCost()) {
            return List.of();
        }
        return reportDao.topPurchaseSupplierInbound(range.startAt(), range.endAt(), SUPPLIER_TOP_LIMIT, form);
    }

    public PageResult<PurchaseReportVO.PurchaserRow> byPurchaser(ScmPurchaseReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        PageResult<PurchaseReportVO.PurchaserRow> result = SmartPageUtil.convert2PageResult(page,
                reportDao.purchaseByPurchaser(page, range.startAt(), range.endAt(), form));
        List<PurchaseReportVO.PurchaserRow> rows = result.getList();
        if (!ScmReportAccess.canViewCost()) {
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
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page,
                reportDao.purchaseItemList(page, range.startAt(), range.endAt(), form));
    }

    public List<PurchaseReportVO.PriceTrendPoint> priceTrend(ScmPurchaseReportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        return reportDao.purchasePriceTrend(range.startAt(), range.endAt(), form);
    }

    private void maskProductCost(PageResult<PurchaseReportVO.ProductRow> result) {
        if (ScmReportAccess.canViewCost() || result == null || result.getList() == null) {
            return;
        }
        result.getList().forEach(row -> {
            row.setInboundCostAmount(null);
            row.setInboundCostMissingCount(null);
            row.setInboundQuantityText(null);
        });
    }

    private void maskSupplierCost(PageResult<PurchaseReportVO.SupplierRow> result) {
        if (ScmReportAccess.canViewCost() || result == null || result.getList() == null) {
            return;
        }
        result.getList().forEach(row -> {
            row.setInboundCostAmount(null);
            row.setInboundCostMissingCount(null);
        });
    }
}
