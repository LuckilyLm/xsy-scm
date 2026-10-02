package com.xsy.scm.report.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.report.dao.ScmFinanceAgingDao;
import com.xsy.scm.report.domain.form.ScmFinanceAgingQueryForm;
import com.xsy.scm.report.domain.vo.ScmFinanceAgingRowVO;
import com.xsy.scm.report.domain.vo.ScmFinanceAgingSummaryVO;
import com.xsy.scm.report.support.ScmReportExportGuard;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScmFinanceAgingService {
    private final ScmFinanceAgingDao financeAgingDao;
    private final ScmDataScopeService dataScopeService;

    public PageResult<ScmFinanceAgingRowVO> query(ScmFinanceAgingQueryForm form) {
        OffsetDateTime endAt = validate(form);
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (denied(form, scope)) {
            return ScmDataScopeService.emptyPage(form);
        }
        Page<?> page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page, financeAgingDao.query(page, form, endAt, scope));
    }

    public List<ScmFinanceAgingSummaryVO> summary(ScmFinanceAgingQueryForm form) {
        OffsetDateTime endAt = validate(form);
        ScmDataScopeContext scope = dataScopeService.resolve();
        return denied(form, scope) ? List.of() : financeAgingDao.summary(form, endAt, scope);
    }

    public List<ScmFinanceAgingRowVO> export(ScmFinanceAgingQueryForm form) {
        OffsetDateTime endAt = validate(form);
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (denied(form, scope)) {
            return List.of();
        }
        return ScmReportExportGuard.exportRows(size ->
                financeAgingDao.query(new Page<>(1, size, false), form, endAt, scope));
    }

    private OffsetDateTime validate(ScmFinanceAgingQueryForm form) {
        SalesReportService.rejectClientSort(form);
        if (form.getAccountType() == null || form.getAsOfDate() == null
                || (form.isReceivable() && form.getSupplierId() != null)
                || (!form.isReceivable() && (form.getCustomerId() != null || form.getSettlementCustomerId() != null))) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        return form.getAsOfDate().plusDays(1).atStartOfDay(ScmReportTimeRangeResolver.BUSINESS_ZONE).toOffsetDateTime();
    }

    private boolean denied(ScmFinanceAgingQueryForm form, ScmDataScopeContext scope) {
        return scope.getWarehouseScope().isEmpty()
                || (form.getWarehouseId() != null && !scope.getWarehouseScope().allows(form.getWarehouseId()))
                || (form.isReceivable()
                        ? scope.getOrderSellerScope().isEmpty() || scope.getCustomerSellerScope().isEmpty()
                        : scope.getPurchaserScope().isEmpty());
    }
}
