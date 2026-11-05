package com.xsy.scm.common.time;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 业务日期区间换算后的时间范围，左闭右开。
 */
public record ScmDateTimeRange(LocalDate startDate, LocalDate endDate, OffsetDateTime startAt, OffsetDateTime endAt) {

}
