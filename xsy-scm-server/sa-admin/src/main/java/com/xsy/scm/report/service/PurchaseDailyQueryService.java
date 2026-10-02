package com.xsy.scm.report.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.report.constant.ReportErrorCode;
import com.xsy.scm.report.dao.PurchaseDailyReportDao;
import com.xsy.scm.report.domain.form.PurchaseDailyQueryForm;
import com.xsy.scm.report.domain.vo.PurchaseDailyReportVO;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.util.SmartPageUtil;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseDailyQueryService {
    private final PurchaseDailyReportDao purchaseDailyReportDao;
    private final ScmDataScopeService scmDataScopeService;

    public PurchaseDailyReportVO query(PurchaseDailyQueryForm form) {
        SalesReportService.rejectClientSort(form);
        if (form.getReportDate() == null) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_DATE_RANGE_REQUIRED);
        }
        PurchaseDailyReportVO result = new PurchaseDailyReportVO();
        result.setReportDate(form.getReportDate());
        result.setProducts(ScmDataScopeService.emptyPage(form));
        ScmDataScopeContext context = scmDataScopeService.resolve();
        ScmValueScope warehouseScope = context.getWarehouseScope().narrow(form.getWarehouseId());
        if (warehouseScope.isEmpty() || context.getPurchaserScope().isEmpty()) {
            return result;
        }
        result.setGeneratedAt(purchaseDailyReportDao.findGeneratedAt(form.getReportDate()));
        if (result.getGeneratedAt() == null) {
            return result;
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        result.setProducts(SmartPageUtil.convert2PageResult(page,
                purchaseDailyReportDao.queryProducts(page, form, warehouseScope, context.getPurchaserScope())));
        return result;
    }
}
