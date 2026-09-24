package net.lab1024.sa.admin.module.scm.report.support;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 解析后的报表查询区间。
 *
 * @param startDate 用户选择的起始日（闭）
 * @param endDate   用户选择的结束日（闭）
 * @param startAt   {@code startDate} 在 Asia/Shanghai 的日界瞬间（含）
 * @param endAt     {@code endDate + 1 天} 在 Asia/Shanghai 的日界瞬间（不含）
 */
public record ScmReportTimeRange(LocalDate startDate,
                                 LocalDate endDate,
                                 OffsetDateTime startAt,
                                 OffsetDateTime endAt) {
}
