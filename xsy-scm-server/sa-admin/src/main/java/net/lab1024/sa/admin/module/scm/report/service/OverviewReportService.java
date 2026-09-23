package net.lab1024.sa.admin.module.scm.report.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.report.dao.ReportDao;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmOverviewReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReportDailyStatVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReportOverviewVO;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportAccess;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportTimeRange;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportTimeRangeResolver;

/**
 * 经营概览（只读）。趋势与每日统计共用同一条 SQL，因此图上那个点与表里那一行必然同口径。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OverviewReportService {

    private final ReportDao reportDao;

    public ReportOverviewVO overview(ScmOverviewReportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ReportOverviewVO vo = reportDao.overviewKpi(range.startAt(), range.endAt(), form);
        if (vo != null && !ScmReportAccess.canViewCost()) {
            vo.setPurchaseInCostAmount(null);
            vo.setPurchaseInCostMissingCount(null);
            vo.setInventoryBookValue(null);
        }
        return vo;
    }

    public List<ReportDailyStatVO> dailyStat(ScmOverviewReportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        List<ReportDailyStatVO> rows = reportDao.dailyStat(range.startDate(), range.endDate(),
                range.startAt(), range.endAt(), form);
        if (!ScmReportAccess.canViewCost()) {
            rows.forEach(row -> {
                row.setPurchaseInCostAmount(null);
                row.setPurchaseInCostMissingCount(null);
            });
        }
        return rows;
    }
}
