package com.xsy.scm.common.time;

import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.error.ScmErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 业务日期转北京时间左闭右开区间的公共方法。
 */
public final class ScmDateTimeRangeResolver {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    public static final int MAX_SPAN_DAYS = 366;

    private ScmDateTimeRangeResolver() {
    }

    public static ScmDateTimeRange resolve(LocalDate startDate, LocalDate endDate) {
        return resolve(startDate, endDate, ScmCommonErrorCode.VALIDATION_ERROR);
    }

    /** 日期跨度超限时抛的错误码由调用方传入。 */
    public static ScmDateTimeRange resolve(LocalDate startDate, LocalDate endDate, ScmErrorCode spanError) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > MAX_SPAN_DAYS) {
            throw new ScmBusinessException(spanError);
        }
        return new ScmDateTimeRange(startDate, endDate, startDate.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime(),
            endDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime());
    }
}
