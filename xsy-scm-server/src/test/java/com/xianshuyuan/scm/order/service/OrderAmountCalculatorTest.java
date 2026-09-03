package com.xianshuyuan.scm.order.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderAmountCalculatorTest {

    @Test
    void calculatesLineAmountWithFourDecimalHalfUpRounding() {
        BigDecimal amount = OrderAmountCalculator.lineAmount(
            new BigDecimal("1.23455"),
            BigDecimal.ONE
        );

        assertThat(amount).isEqualByComparingTo("1.2346");
        assertThat(amount.scale()).isEqualTo(4);
    }

    @Test
    void roundsEachLineBeforeCalculatingOrderTotal() {
        BigDecimal total = OrderAmountCalculator.orderAmount(List.of(
            OrderAmountCalculator.lineAmount(new BigDecimal("0.33335"), BigDecimal.ONE),
            OrderAmountCalculator.lineAmount(new BigDecimal("0.33335"), BigDecimal.ONE)
        ));

        assertThat(total).isEqualByComparingTo("0.6668");
        assertThat(total.scale()).isEqualTo(4);
    }

    @Test
    void representsAnEmptyOrderTotalAtFourDecimalPlaces() {
        BigDecimal total = OrderAmountCalculator.orderAmount(List.of());

        assertThat(total).isEqualByComparingTo("0.0000");
        assertThat(total.scale()).isEqualTo(4);
    }
}
