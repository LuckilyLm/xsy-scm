package com.xsy.scm.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.finance.dao.FinancePaymentSourceDao;
import com.xsy.scm.finance.domain.form.FinanceRefundOptionQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceRefundOptionVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/** Read-only, seller-scoped selector for completed refunds that have not been paid yet. */
@Service
@RequiredArgsConstructor
public class FinanceRefundOptionQueryService {

    private final FinancePaymentSourceDao financePaymentSourceDao;
    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<FinanceRefundOptionVO> query(FinanceRefundOptionQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.getCustomerSellerScope().isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        if (form.getSortItemList() != null && !form.getSortItemList().isEmpty()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        if (form.getPageNum() == null || form.getPageNum() < 1) {
            form.setPageNum(1L);
        }
        if (form.getPageSize() == null || form.getPageSize() < 1) {
            form.setPageSize(20L);
        }
        if (form.getPageSize() > 100) {
            form.setPageSize(100L);
        }
        if (form.getKeyword() != null) {
            form.setKeyword(form.getKeyword().trim());
        }
        Page<?> page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        List<FinanceRefundOptionVO> rows = financePaymentSourceDao.selectCompletedRefundOptions(page, form, scope);
        return SmartPageUtil.convert2PageResult(page, rows);
    }
}
