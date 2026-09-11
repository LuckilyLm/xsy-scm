package com.xianshuyuan.scm.order.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEnumsTest {

    @Test
    void exposesSupportedOrderSources() {
        assertThat(OrderSource.values()).containsExactly(
            OrderSource.NORMAL,
            OrderSource.SUPPLEMENT,
            OrderSource.MALL
        );
    }

    @Test
    void exposesSupportedPriceSources() {
        assertThat(PriceSource.values()).containsExactly(
            PriceSource.AGREEMENT,
            PriceSource.MARKET,
            PriceSource.OVERRIDE
        );
    }

    @Test
    void exposesSupportedQuantitySources() {
        assertThat(QuantitySource.values()).containsExactly(
            QuantitySource.SYSTEM,
            QuantitySource.MANUAL
        );
    }
}
