package com.xsy.scm.finance.support;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.finance.domain.form.FinanceDatePageForm;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * 财务列表共用的分页归一化与客户端排序拒绝。
 */
public final class FinanceQuerySupport {

    private FinanceQuerySupport() {
    }

    public static void rejectClientSort(FinanceDatePageForm form) {
        if (form.getSortItemList() != null && !form.getSortItemList().isEmpty()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }

    public static void normalizePage(FinanceDatePageForm form) {
        if (form.getPageNum() == null || form.getPageNum() < 1) {
            form.setPageNum(1L);
        }
        if (form.getPageSize() == null || form.getPageSize() < 1) {
            form.setPageSize(20L);
        }
    }
}
