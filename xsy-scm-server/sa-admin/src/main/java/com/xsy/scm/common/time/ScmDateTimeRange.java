package com.xsy.scm.common.time;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** A business-date range translated to a half-open timestamp range. */
public record ScmDateTimeRange(LocalDate startDate, LocalDate endDate, OffsetDateTime startAt, OffsetDateTime endAt) {
}
