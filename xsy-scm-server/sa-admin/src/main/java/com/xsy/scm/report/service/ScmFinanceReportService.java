package com.xsy.scm.report.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.report.dao.ScmFinanceReportDao;
import com.xsy.scm.report.domain.form.ScmFinanceOverviewQueryForm;
import com.xsy.scm.report.domain.form.ScmFinanceReportExportQueryForm;
import com.xsy.scm.report.domain.form.ScmFinanceReportQueryForm;
import com.xsy.scm.report.domain.vo.ScmFinanceOverviewVO;
import com.xsy.scm.report.domain.vo.ScmFinancePayableDetailVO;
import com.xsy.scm.report.domain.vo.ScmFinanceReceivableDetailVO;
import com.xsy.scm.report.support.ScmReportExportGuard;
import com.xsy.scm.report.support.ScmReportTimeRange;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Finance R0 read model. All balances are derived from append-only Finance R1 facts. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScmFinanceReportService {

    private final ScmFinanceReportDao financeReportDao;

    private final ScmDataScopeService dataScopeService;

    public ScmFinanceOverviewVO overview(ScmFinanceOverviewQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        return financeReportDao.overview(range.startAt(), range.endAt(), visibleScope(context.getOrderSellerScope()),
                visibleScope(context.getPurchaserScope()));
    }

    public PageResult<ScmFinanceReceivableDetailVO> receivableDetails(ScmFinanceReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmValueScope scope = dataScopeService.resolve().getOrderSellerScope();
        if (scope.isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        Page<?> page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page,
                financeReportDao.receivableDetails(page, range.endAt(), form, scope));
    }

    public PageResult<ScmFinancePayableDetailVO> payableDetails(ScmFinanceReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmValueScope scope = dataScopeService.resolve().getPurchaserScope();
        if (scope.isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        Page<?> page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page,
                financeReportDao.payableDetails(page, range.endAt(), form, scope));
    }

    public List<ScmFinanceReceivableDetailVO> exportReceivableDetails(ScmFinanceReportExportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmValueScope scope = dataScopeService.resolve().getOrderSellerScope();
        if (scope.isEmpty()) {
            return List.of();
        }
        return ScmReportExportGuard.exportRows(pageSize -> {
            Page<ScmFinanceReceivableDetailVO> page = new Page<>(1, pageSize);
            return financeReportDao.receivableDetails(page, range.endAt(), form, scope);
        });
    }

    public List<ScmFinancePayableDetailVO> exportPayableDetails(ScmFinanceReportExportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmValueScope scope = dataScopeService.resolve().getPurchaserScope();
        if (scope.isEmpty()) {
            return List.of();
        }
        return ScmReportExportGuard.exportRows(pageSize -> {
            Page<ScmFinancePayableDetailVO> page = new Page<>(1, pageSize);
            return financeReportDao.payableDetails(page, range.endAt(), form, scope);
        });
    }

    private static ScmValueScope visibleScope(ScmValueScope scope) {
        return scope.isEmpty() ? null : scope;
    }
}
