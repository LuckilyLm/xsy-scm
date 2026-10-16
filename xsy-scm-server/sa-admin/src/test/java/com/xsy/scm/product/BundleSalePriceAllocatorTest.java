package com.xsy.scm.product;

import com.xsy.scm.product.manager.BundleSalePriceAllocator;
import com.xsy.scm.product.manager.BundleSalePriceAllocator.Component;
import com.xsy.scm.product.manager.BundleSalePriceAllocator.Share;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BundleSalePriceAllocatorTest {

    @Test
    void splitsIndependentBundlePriceByMarketAmountsNotUnitPrices() {
        List<Share> shares = BundleSalePriceAllocator.allocate(price("80.0000"), List.of(
                component(101L, "2.0000", "10.0000"),
                component(102L, "3.0000", "10.0000")));

        assertThat(shares).hasSize(2);
        assertThat(shares.get(0).referenceAmount()).isEqualByComparingTo("20.0000");
        assertThat(shares.get(1).referenceAmount()).isEqualByComparingTo("30.0000");
        assertThat(shares.get(0).allocatedSaleAmount()).isEqualByComparingTo("32.0000");
        assertThat(shares.get(1).allocatedSaleAmount()).isEqualByComparingTo("48.0000");
        assertThat(total(shares)).isEqualByComparingTo("80.0000");
    }

    @Test
    void putsRoundingDifferenceInLastComponent() {
        List<Share> shares = BundleSalePriceAllocator.allocate(price("1.0000"), List.of(
                component(101L, "1.0000", "1.0000"),
                component(102L, "1.0000", "1.0000"),
                component(103L, "1.0000", "1.0000")));

        assertThat(shares.get(0).allocatedSaleAmount()).isEqualByComparingTo("0.3333");
        assertThat(shares.get(1).allocatedSaleAmount()).isEqualByComparingTo("0.3333");
        assertThat(shares.get(2).allocatedSaleAmount()).isEqualByComparingTo("0.3334");
        assertThat(total(shares)).isEqualByComparingTo("1.0000");
    }

    @Test
    void tinyAndZeroBundlePricesNeverCreateNegativeAllocations() {
        var components = List.of(component(1L, "1", "1"), component(2L, "1", "1"),
                component(3L, "1", "1"));
        var tiny = BundleSalePriceAllocator.allocate(price("0.0001"), components);
        assertThat(total(tiny)).isEqualByComparingTo("0.0001");
        assertThat(tiny).allSatisfy(row ->
                assertThat(row.allocatedSaleAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO));

        var free = BundleSalePriceAllocator.allocate(price("0.0000"), components);
        assertThat(free).allSatisfy(row ->
                assertThat(row.allocatedSaleAmount()).isEqualByComparingTo("0.0000"));
        assertThat(total(free)).isEqualByComparingTo("0.0000");
    }

    @Test
    void prohibitsMissingMarketPriceInvalidQuantityAndDuplicateComponents() {
        assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("80"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("80"), List.of(
                component(1L, "1", "10"), component(1L, "2", "20"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("80"), List.of(
                component(1L, "0", "10")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("80"), List.of(
                component(1L, "1", "0")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("80"), List.of(
                component(1L, "1", null)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("-1"), List.of(
                component(1L, "1", "10")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("1.00001"), List.of(
                component(1L, "1", "10")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("100000000000000.0000"), List.of(
                component(1L, "1", "10")))).isInstanceOf(IllegalArgumentException.class);
    }

    private static Component component(Long sku, String qty, String market) {
        return new Component(sku, price(qty), market == null ? null : price(market));
    }

    private static BigDecimal price(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal total(List<Share> shares) {
        return shares.stream().map(Share::allocatedSaleAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
