package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.dto.AgreementPriceSaveRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgreementPriceValidatorTest {
    private final AgreementPriceValidator validator = new AgreementPriceValidator();
    private final OffsetDateTime now = OffsetDateTime.parse("2026-09-03T10:00:00+08:00");

    @Test void rejectsNegativePrice() {
        assertThatThrownBy(() -> validator.validate(request("-0.0001", now, null)))
            .isInstanceOf(BusinessException.class).hasMessageContaining("不能小于零");
    }

    @Test void rejectsEmptyOrReversedPeriod() {
        assertThatThrownBy(() -> validator.validate(request("1.0000", now, now)))
            .isInstanceOf(BusinessException.class).hasMessageContaining("结束时间");
    }

    private AgreementPriceSaveRequest request(String price, OffsetDateTime from, OffsetDateTime to) {
        return new AgreementPriceSaveRequest(null, 1L, 2L, new BigDecimal(price), from, to);
    }
}
