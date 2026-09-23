package net.lab1024.sa.admin.module.scm.report.support;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.report.constant.ReportErrorCode;

/**
 * 报表唯一允许的日界换算入口（Finance R0 计划 §28）。
 *
 * <p>库中时间列是 {@code TIMESTAMPTZ}，而用户选的是「哪几天」。两者之间的换算规则就是口径本身：
 * 起始日 00:00（含）到结束日次日 00:00（不含）。这里刻意用固定的 Asia/Shanghai 而不是 JVM 默认时区——
 * 应用容器时区一变，同一份报表的历史数字就会集体漂移一位，且很难归因。
 */
public final class ScmReportTimeRangeResolver {

    /** 与业务库、单号生成器、大屏一致的日界时区。 */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 最大查询跨度（含首尾两天）。超过这个范围的历史聚合没有可用的索引边界，
     * 且趋势图与每日统计的行数也不再是可读的。
     */
    public static final int MAX_SPAN_DAYS = 366;

    private ScmReportTimeRangeResolver() {
    }

    /**
     * 校验并把闭区间日期解析成半开区间瞬间。
     *
     * @throws ScmBusinessException 日期缺失 / 倒序（40000），或跨度超限（41111）
     */
    public static ScmReportTimeRange resolve(ScmReportDateFilter filter) {
        LocalDate startDate = filter.getStartDate();
        LocalDate endDate = filter.getEndDate();
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > MAX_SPAN_DAYS) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_DATE_RANGE_TOO_LARGE);
        }
        return new ScmReportTimeRange(startDate, endDate,
                startDate.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime(),
                endDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime());
    }
}
