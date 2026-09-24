package net.lab1024.sa.admin.module.scm.report.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.admin.module.scm.report.dao.ReportDao;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmOverviewReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReportDailyStatVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReportOverviewVO;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportTimeRange;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportTimeRangeResolver;

/**
 * 经营概览（只读）。趋势与每日统计共用同一条 SQL，因此图上那个点与表里那一行必然同口径。
 *
 * <p><b>仓库范围只收窄能按仓库归属的指标</b>：采购与库存四个派生表按调用者的仓库授权范围取数，
 * 而销售与退款指标所在的 {@code sales_order} / {@code order_refund} 没有仓库列，
 * 既不能按仓库收窄，也不得拿 {@code created_by} 之类的字段顶替（裁决第 3 条）。
 * 因此仓库范围为空时，采购与库存指标返回 {@code null}（页面显示 {@code —}）而不是 0 ——
 * 0 会把它谎报成「这些仓库里没有数据」，而真实原因是「你没有可看的仓库」。
 * 两个方法抹的字段必须一致，否则指标卡与趋势图会对不上。
 */
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
                warehousePredicate(context));
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
        List<ReportDailyStatVO> rows = reportDao.dailyStat(range.startDate(), range.endDate(),
                range.startAt(), range.endAt(), form, warehousePredicate(context));
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
     * 概览是「一条 SQL 里混着两类指标」的例外：销售与退款指标没有仓库维度，不能因为调用者
     * 一个仓都没授权就消失，所以这里不能像列表那样整体短路掉。
     *
     * <p>但空授权清单也不能直接下传 —— {@code IN ()} 是非法 SQL。XML 把 {@code scope == null}
     * 读成恒假（失败关闭），正好是「采购与库存这四个派生表一行都不取」的合法表达，
     * 于是范围在此处保持「不知道」，再由 {@link #clearWarehouseMetrics} 把那些零抹成 null。
     * 其余按仓库归属的查询都在 Service 层就短路了，不需要这个写法。
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
