package com.xsy.scm.report.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.report.dao.ScmFinanceProfitDao;
import com.xsy.scm.report.domain.form.ScmFinanceProfitQueryForm;
import com.xsy.scm.report.domain.vo.ScmFinanceProfitRowVO;
import com.xsy.scm.report.domain.vo.ScmFinanceProfitSummaryVO;
import com.xsy.scm.report.support.ScmReportExportGuard;
import com.xsy.scm.report.support.ScmReportTimeRange;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/** Finance gross-profit read model. It does not create or mutate financial facts. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScmFinanceProfitService {
    private final ScmFinanceProfitDao financeProfitDao;
    private final ScmDataScopeService dataScopeService;

    public PageResult<ScmFinanceProfitRowVO> query(ScmFinanceProfitQueryForm form) {
        ScmReportTimeRange range = validate(form);
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (denied(form, scope)) {
            return ScmDataScopeService.emptyPage(form);
        }
        Page<?> page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page,
                financeProfitDao.query(page, range.startAt(), range.endAt(), form, scope));
    }

    public ScmFinanceProfitSummaryVO summary(ScmFinanceProfitQueryForm form) {
        ScmReportTimeRange range = validate(form);
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (denied(form, scope)) {
            return new ScmFinanceProfitSummaryVO();
        }
        return financeProfitDao.summary(range.startAt(), range.endAt(), form, scope);
    }

    public List<ScmFinanceProfitRowVO> export(ScmFinanceProfitQueryForm form) {
        ScmReportTimeRange range = validate(form);
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (denied(form, scope)) {
            return List.of();
        }
        return ScmReportExportGuard.exportRows(pageSize -> financeProfitDao.query(new Page<>(1, pageSize, false),
                range.startAt(), range.endAt(), form, scope));
    }

    private ScmReportTimeRange validate(ScmFinanceProfitQueryForm form) {
        SalesReportService.rejectClientSort(form);
        if (form.getDimension() == null) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        return ScmReportTimeRangeResolver.resolve(form);
    }

    private boolean denied(ScmFinanceProfitQueryForm form, ScmDataScopeContext scope) {
        return !scope.isCostVisible() || scope.getWarehouseScope().isEmpty() || scope.getOrderSellerScope().isEmpty()
                || scope.getCustomerSellerScope().isEmpty()
                || (form.getWarehouseId() != null && !scope.getWarehouseScope().allows(form.getWarehouseId()));
    }
}
