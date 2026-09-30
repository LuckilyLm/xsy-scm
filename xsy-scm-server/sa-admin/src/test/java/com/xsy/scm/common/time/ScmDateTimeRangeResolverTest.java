package com.xsy.scm.common.time;

import com.xsy.scm.common.exception.ScmBusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SCM 日期范围解析")
class ScmDateTimeRangeResolverTest {

    @Test
    @DisplayName("业务日期转换为上海时区的半开区间")
    void resolvesBusinessDatesToHalfOpenRange() {
        LocalDate date = LocalDate.of(2026, 9, 29);

        ScmDateTimeRange range = ScmDateTimeRangeResolver.resolve(date, date);

        assertThat(range.startAt()).isEqualTo(OffsetDateTime.parse("2026-09-29T00:00:00+08:00"));
        assertThat(range.endAt()).isEqualTo(OffsetDateTime.parse("2026-09-30T00:00:00+08:00"));
    }

    @Test
    @DisplayName("366 天包含首尾日可查，367 天拒绝")
    void enforcesMaximumInclusiveSpan() {
        LocalDate start = LocalDate.of(2025, 9, 29);
        LocalDate end = LocalDate.of(2026, 9, 29);

        assertThat(ScmDateTimeRangeResolver.resolve(start, end).startDate()).isEqualTo(start);
        assertThatThrownBy(() -> ScmDateTimeRangeResolver.resolve(start.minusDays(1), end))
                .isInstanceOf(ScmBusinessException.class);
    }

    @Test
    @DisplayName("缺失或倒序日期返回校验错误")
    void rejectsMissingOrReversedDates() {
        LocalDate today = LocalDate.of(2026, 9, 29);

        assertThatThrownBy(() -> ScmDateTimeRangeResolver.resolve(null, today))
                .isInstanceOf(ScmBusinessException.class);
        assertThatThrownBy(() -> ScmDateTimeRangeResolver.resolve(today, today.minusDays(1)))
                .isInstanceOf(ScmBusinessException.class);
    }
}
