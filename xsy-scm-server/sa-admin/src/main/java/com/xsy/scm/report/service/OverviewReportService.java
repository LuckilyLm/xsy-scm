package com.xsy.scm.report.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.report.dao.ReportDao;
import com.xsy.scm.report.domain.form.ScmOverviewReportQueryForm;
import com.xsy.scm.report.domain.vo.ReportDailyStatVO;
import com.xsy.scm.report.domain.vo.ReportOverviewVO;
import com.xsy.scm.report.support.ScmReportTimeRange;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;

/** 经营概览只读，图表和日汇总共用同一统计口径。只有能明确归属仓库的指标才按仓库授权过滤。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OverviewReportService {

    private final ReportDao reportDao;

    private final ScmDataScopeService dataScopeService;

    public ReportOverviewVO overview(ScmOverviewReportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        ReportOverviewVO vo = reportDao.overviewKpi(range.startAt(), range.endAt(), form,
                ScmPurchaseStatusEnum.committedNames(), warehousePredicate(context));
        if (vo == null) {
            return null;
        }
        if (context.warehouseNowhere()) {
            clearWarehouseMetrics(vo);
        }
        if (!context.isCostVisible()) {
            vo.setPurchaseInCostAmount(null);
            vo.setPurchaseInCostMissingCount(null);
            vo.setInventoryBookValue(null);
        }
        return vo;
    }

    public List<ReportDailyStatVO> dailyStat(ScmOverviewReportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        List<ReportDailyStatVO> rows = reportDao.dailyStat(range.startDate(), range.endDate(), range.startAt(),
                range.endAt(), form, warehousePredicate(context));
        boolean warehouseNowhere = context.warehouseNowhere();
        boolean costVisible = context.isCostVisible();
        if (warehouseNowhere || !costVisible) {
            rows.forEach(row -> {
                if (warehouseNowhere) {
                    row.setSubmittedPurchaseAmount(null);
                }
                if (!costVisible) {
                    row.setPurchaseInCostAmount(null);
                    row.setPurchaseInCostMissingCount(null);
                }
            });
        }
        return rows;
    }

    /**
     * 概览是「一条 SQL 里混着两类指标」的例外：销售与退款指标没有仓库维度，不能因为调用者一个仓都没授权就消失，所以这里不能像列表那样整体短路掉。
     *
     * <p>
     * 但空授权清单也不能直接下传 —— {@code IN ()} 是非法 SQL。XML 把 {@code scope == null} 读成恒假（失败关闭），正好是「采购与库存这四个派生表一行都不取」的合法表达，
     * 于是范围在此处保持「不知道」，再由 {@link #clearWarehouseMetrics} 把那些零抹成 null。其余按仓库归属的查询都在 Service 层就短路了，不需要这个写法。
     */
    private static ScmValueScope warehousePredicate(ScmDataScopeContext context) {
        return context.warehouseNowhere() ? null : context.getWarehouseScope();
    }

    /** 与 {@link #overview} 的采购 / 库存指标同名同范围，日期轴上的每一行都按同一规则抹除。 */
    private void clearWarehouseMetrics(ReportOverviewVO vo) {
        vo.setSubmittedPurchaseOrderCount(null);
        vo.setSubmittedPurchaseAmount(null);
        vo.setPurchaseInCostAmount(null);
        vo.setPurchaseInCostMissingCount(null);
        vo.setInventoryBookValue(null);
        vo.setStockedSkuCount(null);
    }
}
