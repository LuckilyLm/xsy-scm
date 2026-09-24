package net.lab1024.sa.admin.module.scm.report.support;

import java.util.List;
import java.util.function.Function;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.report.constant.ReportErrorCode;

/**
 * 报表同步导出的规模守卫（Finance R0 计划 §38）。
 *
 * <p>取 {@code EXPORT_MAX_ROWS + 1} 行而不是先数一遍：只要多取一行就能判定「是否超限」，
 * 省掉一次与真实查询同样昂贵的 {@code count(*)}。判定依据是<b>当前筛选的完整结果</b>，
 * 不是当前页。
 */
public final class ScmReportExportGuard {

    /** 同步导出行数上限。超过即拒绝，引导用户缩小范围，而不是静默只导前 N 行。 */
    public static final int EXPORT_MAX_ROWS = 100_000;

    /** 探超限所需的多取一行。 */
    public static final long PROBE_PAGE_SIZE = EXPORT_MAX_ROWS + 1L;

    private ScmReportExportGuard() {
    }

    /**
     * 以「第 1 页 + 上限多一行」执行 {@code query}，超限则抛 41112，否则返回可导出的完整行集。
     *
     * <p>调用方必须保证 {@code query} 只读取一次数据；本方法不会二次执行它，
     * 否则导出可能与列表口径出现第二次分叉。
     */
    public static <T> List<T> exportRows(Function<Long, List<T>> queryByPageSize) {
        List<T> rows = queryByPageSize.apply(PROBE_PAGE_SIZE);
        if (rows != null && rows.size() > EXPORT_MAX_ROWS) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_EXPORT_ROW_LIMIT_EXCEEDED);
        }
        return rows == null ? List.of() : rows;
    }
}
