package net.lab1024.sa.admin.module.scm.order.manager;

import net.lab1024.sa.admin.module.scm.order.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import static net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode.*;
import java.math.BigDecimal;
import java.util.*;
public final class OrderAmountCalculator {
    public static final int SCALE=4;
    private static final BigDecimal MAX=new BigDecimal("99999999999999.9999");
    private OrderAmountCalculator() {}
    public static BigDecimal bounded(BigDecimal value) {
        if(value==null) return null;
        value=value.setScale(SCALE,java.math.RoundingMode.HALF_UP);
        if(value.abs().compareTo(MAX)>0) throw new ScmBusinessException(ORDER_PRICE_INVALID);
        return value;
    }
    public static BigDecimal lineAmount(BigDecimal quantity,BigDecimal price) {
        return price==null ? null : bounded(quantity.multiply(price));
    }
    public static BigDecimal orderAmount(List<BigDecimal> lines) {
        if(lines.stream().anyMatch(Objects::isNull)) return null;
        return bounded(lines.stream().reduce(BigDecimal.ZERO,BigDecimal::add));
    }
}
