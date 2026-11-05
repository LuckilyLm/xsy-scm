package com.xsy.scm.report.support;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 解析后的报表查询区间。
 *
 * <p>
 * 日期是用户选择的闭区间，时刻是两个日界瞬间：{@code startAt} 含，{@code endAt} 为 {@code endDate + 1 天} 的日界、不含，两端都在 Asia/Shanghai 上解析。
 */
public record ScmReportTimeRange(LocalDate startDate, LocalDate endDate, OffsetDateTime startAt, OffsetDateTime endAt) {
}
