package com.xianshuyuan.scm.order.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesOrderNumberGeneratorTest {

    @Test
    void combinesSalesOrderPrefixDateAndZeroPaddedSequenceValue() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT nextval('sales_order_no_seq')", Long.class)).thenReturn(42L);
        SalesOrderNumberGenerator generator = new SalesOrderNumberGenerator(
            jdbcTemplate, () -> LocalDate.of(2026, 9, 3)
        );

        assertThat(generator.next()).isEqualTo("SO20260903000042");
    }
}
