package com.xianshuyuan.scm.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class AfterSalesRules {
    private AfterSalesRules() { }

    public static void requireWithinActual(BigDecimal actual, BigDecimal reserved, BigDecimal requested) {
        if (requested == null || requested.signum() <= 0) throw new AfterSalesRuleException("退货数量必须大于零");
        if (actual == null || reserved.add(requested).compareTo(actual) > 0) throw new AfterSalesRuleException("退货数量超过可退数量");
    }

    public static void requirePositiveApproval(List<BigDecimal> quantities) {
        if (quantities.stream().noneMatch(value -> value != null && value.signum() > 0)) throw new AfterSalesRuleException("至少一行批准数量必须大于零");
    }

    public static BigDecimal amount(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);
    }
}
