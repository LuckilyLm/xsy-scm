package com.xsy.scm.payment.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.payment.constant.PaymentErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 已按稳定来源顺序排列的本金比例分摊；尾差从第一来源起逐个归集。
 */
@Component
public class RefundFundingAllocator {
    private static final int SCALE = 4;
    private static final BigDecimal UNIT = new BigDecimal("0.0001");

    public List<BigDecimal> allocate(BigDecimal requestedAmount, List<BigDecimal> principals) {
        if (requestedAmount == null || principals == null || principals.isEmpty()
            || principals.stream().anyMatch(value -> value == null || value.signum() <= 0)) {
            throw new ScmBusinessException(PaymentErrorCode.REFUND_FUNDING_INVALID);
        }
        BigDecimal requested = requestedAmount.setScale(SCALE, RoundingMode.UNNECESSARY);
        BigDecimal total = principals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (requested.signum() <= 0 || requested.compareTo(total) > 0) {
            throw new ScmBusinessException(PaymentErrorCode.REFUND_FUNDING_INVALID);
        }
        List<BigDecimal> result = new ArrayList<>(principals.size());
        BigDecimal allocated = BigDecimal.ZERO.setScale(SCALE);
        for (BigDecimal principal : principals) {
            BigDecimal amount = requested.multiply(principal).divide(total, SCALE, RoundingMode.DOWN);
            result.add(amount);
            allocated = allocated.add(amount);
        }
        BigDecimal remainder = requested.subtract(allocated);
        int index = 0;
        while (remainder.signum() > 0) {
            result.set(index, result.get(index).add(UNIT));
            remainder = remainder.subtract(UNIT);
            index = (index + 1) % result.size();
        }
        return result;
    }
}
