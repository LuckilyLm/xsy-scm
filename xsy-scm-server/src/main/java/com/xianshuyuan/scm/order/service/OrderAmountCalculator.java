package com.xianshuyuan.scm.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class OrderAmountCalculator {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);

    private OrderAmountCalculator() {
    }

    public static BigDecimal lineAmount(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(SCALE, ROUNDING_MODE);
    }

    public static BigDecimal orderAmount(List<BigDecimal> lineAmounts) {
        return lineAmounts.stream()
                .reduce(ZERO, BigDecimal::add)
                .setScale(SCALE, ROUNDING_MODE);
    }
}
