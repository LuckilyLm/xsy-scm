package com.xsy.scm.order.manager;



import com.xsy.scm.common.exception.ScmBusinessException;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_PRICE_INVALID;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class OrderAmountCalculator {
    public static final int SCALE = 4;
    private static final BigDecimal MAX = new BigDecimal("99999999999999.9999");

    private OrderAmountCalculator() {
    }

    public static BigDecimal bounded(BigDecimal amount) {
        if (amount == null) return null;
        amount = amount.setScale(SCALE, java.math.RoundingMode.HALF_UP);
        if (amount.abs().compareTo(MAX) > 0) throw new ScmBusinessException(ORDER_PRICE_INVALID);
        return amount;
    }

    public static BigDecimal lineAmount(BigDecimal quantity, BigDecimal price) {
        return price == null ? null : bounded(quantity.multiply(price));
    }

    public static BigDecimal orderAmount(List<BigDecimal> lines) {
        if (lines.stream().anyMatch(Objects::isNull)) return null;
        return bounded(lines.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
