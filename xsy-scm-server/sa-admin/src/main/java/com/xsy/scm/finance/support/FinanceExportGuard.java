package com.xsy.scm.finance.support;

import com.xsy.scm.common.error.ScmExportErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;

import java.util.List;
import java.util.function.Function;

/** Bounds synchronous finance exports without changing the list query's filters or scope. */
public final class FinanceExportGuard {

    public static final int EXPORT_MAX_ROWS = 100_000;

    public static final long PROBE_PAGE_SIZE = EXPORT_MAX_ROWS + 1L;

    private FinanceExportGuard() {
    }

    public static <T> List<T> exportRows(Function<Long, List<T>> queryByPageSize) {
        List<T> rows = queryByPageSize.apply(PROBE_PAGE_SIZE);
        if (rows != null && rows.size() > EXPORT_MAX_ROWS) {
            throw new ScmBusinessException(ScmExportErrorCode.EXPORT_ROW_LIMIT_EXCEEDED);
        }
        return rows == null ? List.of() : rows;
    }
}
