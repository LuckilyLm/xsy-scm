package com.xsy.scm.finance.support;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.common.time.ScmDateTimeRange;
import com.xsy.scm.common.time.ScmDateTimeRangeResolver;
import com.xsy.scm.finance.domain.form.FinanceDatePageForm;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.util.XsyPageUtil;

import java.util.List;
import java.util.function.BiFunction;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * 财务列表共用的分页归一化、客户端排序拒绝与列表/导出查询骨架。
 */
public final class FinanceQuerySupport {

    private FinanceQuerySupport() {
    }

    /**
     * 财务列表分页查询：校验筛选、归一化分页，再交给调用方按已解析的日期区间查一页。
     */
    public static <T, F extends FinanceDatePageForm> PageResult<T> query(F form, Runnable validateFilters,
        BiFunction<Page<?>, ScmDateTimeRange, List<T>> pageLoader) {
        ScmDateTimeRange range = prepare(form, validateFilters);
        Page<?> page = XsyPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return XsyPageUtil.convert2PageResult(page, pageLoader.apply(page, range));
    }

    /** 带数据范围的列表查询：范围为空时不查库，直接返回空页。 */
    public static <T, F extends FinanceDatePageForm> PageResult<T> query(F form, ScmValueScope scope,
        Runnable validateFilters, BiFunction<Page<?>, ScmDateTimeRange, List<T>> pageLoader) {
        return scope.isEmpty() ? ScmDataScopeService.emptyPage(form) : query(form, validateFilters, pageLoader);
    }

    /** 财务导出行选择：一次取满导出上限并校验行数。 */
    public static <T, F extends FinanceDatePageForm> List<T> exportRows(F form, Runnable validateFilters,
        BiFunction<Page<?>, ScmDateTimeRange, List<T>> pageLoader) {
        ScmDateTimeRange range = prepare(form, validateFilters);
        return FinanceExportGuard.exportRows(pageSize -> {
            form.setPageNum(1L);
            form.setPageSize(pageSize);
            Page<?> page = XsyPageUtil.convert2PageQuery(form);
            page.setSearchCount(false);
            return pageLoader.apply(page, range);
        });
    }

    /** 带数据范围的导出：范围为空时没有可导出的行。 */
    public static <T, F extends FinanceDatePageForm> List<T> exportRows(F form, ScmValueScope scope,
        Runnable validateFilters, BiFunction<Page<?>, ScmDateTimeRange, List<T>> pageLoader) {
        return scope.isEmpty() ? List.of() : exportRows(form, validateFilters, pageLoader);
    }

    private static ScmDateTimeRange prepare(FinanceDatePageForm form, Runnable validateFilters) {
        rejectClientSort(form);
        normalizePage(form);
        validateFilters.run();
        return ScmDateTimeRangeResolver.resolve(form.getStartDate(), form.getEndDate());
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

    public static boolean enumContains(Enum<?>[] values, String value) {
        return java.util.Arrays.stream(values).anyMatch(candidate -> candidate.name().equals(value));
    }
}
