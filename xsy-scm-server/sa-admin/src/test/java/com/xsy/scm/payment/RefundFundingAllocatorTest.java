package com.xsy.scm.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.payment.service.RefundFundingAllocator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 比例与四位小数尾差的纯算法契约；集成用例另验证数据库锁与幂等。 */
class RefundFundingAllocatorTest {
    private final RefundFundingAllocator allocator = new RefundFundingAllocator();

    @Test
    void allocatesProportionallyAndGivesRemainderToStableFirstSource() {
        List<BigDecimal> result = allocator.allocate(new BigDecimal("1.0000"),
                List.of(new BigDecimal("1.0000"), new BigDecimal("1.0000"), new BigDecimal("1.0000")));
        assertThat(result).containsExactly(new BigDecimal("0.3334"), new BigDecimal("0.3333"),
                new BigDecimal("0.3333"));
        assertThat(result.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1.0000");
    }

    @Test
    void keepsPureSourceAsSingleExactAllocation() {
        assertThat(allocator.allocate(new BigDecimal("9.8765"), List.of(new BigDecimal("20.0000"))))
                .containsExactly(new BigDecimal("9.8765"));
    }
}
