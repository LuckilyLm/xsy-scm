package com.xsy.scm.inventory.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventorySaleUnitConverterTest {
    @Test
    void oneBoxIsTenKilograms() {
        BigDecimal actual = InventorySaleUnitConverter.toStockQuantity(
                new BigDecimal("3.0000"), "kg", new BigDecimal("10.0000"));
        assertThat(actual).isEqualByComparingTo("30.0000");
    }

    @Test
    void legacyWithoutSnapshotRetainsOriginalQuantity() {
        assertThat(InventorySaleUnitConverter.toStockQuantity(new BigDecimal("3.25"), null, null))
                .isEqualByComparingTo("3.25");
    }

    @Test
    void incompleteOrInvalidSnapshotMustFail() {
        assertThatThrownBy(() -> InventorySaleUnitConverter.toStockQuantity(
                new BigDecimal("2"), "kg", null)).isInstanceOf(ScmBusinessException.class);
        assertThatThrownBy(() -> InventorySaleUnitConverter.toStockQuantity(
                new BigDecimal("2"), "kg", BigDecimal.ZERO)).isInstanceOf(ScmBusinessException.class);
    }

    @Test
    void returnAllocationConvertsStockBalanceBackToSaleUnitWithoutOverAllocating() {
        // 发出 30 kg（3 箱），先退回 10 kg（1 箱），剩 20 kg 只能再退 2 箱。
        BigDecimal availableStock = new BigDecimal("30.0000").subtract(new BigDecimal("10.0000"));
        BigDecimal availableSale = InventorySaleUnitConverter.availableSaleQuantity(
                availableStock, "kg", new BigDecimal("10.0000"));
        assertThat(availableSale).isEqualByComparingTo("2.0000");
        assertThat(InventorySaleUnitConverter.toStockQuantity(
                availableSale, "kg", new BigDecimal("10.0000"))).isEqualByComparingTo("20.0000");
    }

    @Test
    void returnAllocationDoesNotRoundUpFractionalSaleUnits() {
        assertThat(InventorySaleUnitConverter.availableSaleQuantity(
                new BigDecimal("1.0000"), "kg", new BigDecimal("3.0000")))
                .isEqualByComparingTo("0.3333");
        assertThat(InventorySaleUnitConverter.availableSaleQuantity(new BigDecimal("2"), null, null))
                .isEqualByComparingTo("2");
    }

    @Test
    void neverRoundsInventoryFact() {
        assertThatThrownBy(() -> InventorySaleUnitConverter.toStockQuantity(
                new BigDecimal("0.0001"), "kg", new BigDecimal("0.1234")))
                .isInstanceOf(ScmBusinessException.class);
    }
}
