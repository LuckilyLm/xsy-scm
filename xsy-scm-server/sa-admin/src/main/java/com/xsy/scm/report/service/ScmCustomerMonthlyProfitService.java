package com.xsy.scm.report.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.report.constant.ScmFinanceProfitDimensionEnum;
import com.xsy.scm.report.dao.ScmFinanceProfitDao;
import com.xsy.scm.report.domain.form.ScmCustomerMonthlyProfitQueryForm;
import com.xsy.scm.report.domain.vo.ScmCustomerMonthlyProfitVO;
import com.xsy.scm.report.support.ScmReportExportGuard;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScmCustomerMonthlyProfitService {
    private final ScmFinanceProfitDao profitDao;
    private final ScmDataScopeService dataScopeService;

    public PageResult<ScmCustomerMonthlyProfitVO> query(ScmCustomerMonthlyProfitQueryForm form) {
        ScmDataScopeContext scope = validate(form);
        var range = ScmReportTimeRangeResolver.resolve(form);
        Page<?> page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page,
            profitDao.monthlyQuery(page, range.startAt(), range.endAt(), form, scope));
    }

    public ScmCustomerMonthlyProfitVO summary(ScmCustomerMonthlyProfitQueryForm form) {
        ScmDataScopeContext scope = validate(form);
        var range = ScmReportTimeRangeResolver.resolve(form);
        return profitDao.monthlySummary(range.startAt(), range.endAt(), form, scope);
    }

    public List<ScmCustomerMonthlyProfitVO> export(ScmCustomerMonthlyProfitQueryForm form) {
        ScmDataScopeContext scope = validate(form);
        var range = ScmReportTimeRangeResolver.resolve(form);
        return ScmReportExportGuard.exportRows(
            size -> profitDao.monthlyQuery(new Page<>(1, size, false), range.startAt(), range.endAt(), form, scope));
    }

    private ScmDataScopeContext validate(ScmCustomerMonthlyProfitQueryForm form) {
        SalesReportService.rejectClientSort(form);
        if (form.getStartDate() == null || form.getEndDate() == null || form.getStartDate().getDayOfMonth() != 1
            || !form.getEndDate().equals(form.getStartDate().plusMonths(1).minusDays(1))
            || form.getDimension() != ScmFinanceProfitDimensionEnum.CUSTOMER || form.getWarehouseId() != null
            || form.getSkuId() != null || form.getCategoryId() != null || form.getSellerId() != null
            || (form.getKeyword() != null && !form.getKeyword().isBlank())) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        ScmDataScopeContext scope = dataScopeService.resolve();
        // 月成本与费用属于客户整体，不能用局部订单收入减去整月成本；不依赖仓库成本。
        if (!scope.isCostVisible() || !scope.getOrderSellerScope().isAll()) {
            throw new ScmDataScopeException();
        }
        return scope;
    }
}
