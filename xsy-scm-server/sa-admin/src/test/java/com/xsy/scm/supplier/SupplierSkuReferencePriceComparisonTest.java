package com.xsy.scm.supplier;

import com.xsy.scm.supplier.domain.vo.SupplierSkuVO;
import com.xsy.scm.supplier.manager.SupplierSkuReferencePriceComparison;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierSkuReferencePriceComparisonTest {

    @Test
    void comparesOnlyWithinSamePurchaseUnit() {
        var cheap = source(1L, "公斤", "10.0000");
        var expensive = source(2L, "公斤", "12.5000");
        var box = source(3L, "箱", "100.0000");

        var result = SupplierSkuReferencePriceComparison.compare(List.of(expensive, box, cheap));

        assertThat(result).hasSize(3);
        var boxQuote = result.stream().filter(row -> Long.valueOf(3).equals(row.getSupplierId()))
                .findFirst().orElseThrow();
        assertThat(boxQuote.getPricedSupplierCount()).isEqualTo(1);
        assertThat(boxQuote.getPriceGapAmount()).isNull();

        var kgCheap = result.stream().filter(row -> Long.valueOf(1).equals(row.getSupplierId()))
                .findFirst().orElseThrow();
        var kgExpensive = result.stream().filter(row -> Long.valueOf(2).equals(row.getSupplierId()))
                .findFirst().orElseThrow();
        assertThat(kgCheap.getPriceGapAmount()).isEqualByComparingTo("0.0000");
        assertThat(kgCheap.getPriceGapPercent()).isEqualByComparingTo("0.0000");
        assertThat(kgExpensive.getLowestReferencePrice()).isEqualByComparingTo("10.0000");
        assertThat(kgExpensive.getPriceGapAmount()).isEqualByComparingTo("2.5000");
        assertThat(kgExpensive.getPriceGapPercent()).isEqualByComparingTo("25.0000");
        assertThat(kgExpensive.getPricedSupplierCount()).isEqualTo(2);
    }

    @Test
    void nullReferencePriceIsNotFreeOffer() {
        var missing = source(1L, "公斤", null);
        var only = source(2L, "公斤", "9.0000");

        var result = SupplierSkuReferencePriceComparison.compare(List.of(missing, only));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPricedSupplierCount()).isEqualTo(1);
        assertThat(result.get(0).getPriceGapAmount()).isNull();
        assertThat(result.get(1).getReferencePrice()).isNull();
        assertThat(result.get(1).getLowestReferencePrice()).isEqualByComparingTo("9.0000");
        assertThat(result.get(1).getPriceGapPercent()).isNull();
    }

    @Test
    void zeroMinimumQuoteHasUnknownPercentButKnownAmount() {
        var zero = source(1L, "公斤", "0.0000");
        var paid = source(2L, "公斤", "5.0000");

        var result = SupplierSkuReferencePriceComparison.compare(List.of(zero, paid));

        assertThat(result.get(1).getPriceGapAmount()).isEqualByComparingTo("5.0000");
        assertThat(result.get(1).getPriceGapPercent()).isNull();
        assertThat(result.get(1).getLowestReferencePrice()).isEqualByComparingTo("0");
    }

    @Test
    void samePriceAndEmptySourcesAreHandled() {
        assertThat(SupplierSkuReferencePriceComparison.compare(null)).isEmpty();
        assertThat(SupplierSkuReferencePriceComparison.compare(List.of())).isEmpty();
        var result = SupplierSkuReferencePriceComparison.compare(List.of(
                source(1L, "箱", "8.0000"), source(2L, "箱", "8.0000")));

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(row -> {
            assertThat(row.getPriceGapAmount()).isEqualByComparingTo("0.0000");
            assertThat(row.getPriceGapPercent()).isEqualByComparingTo("0.0000");
        });
    }

    private static SupplierSkuVO source(Long supplierId, String unit, String referencePrice) {
        var row = new SupplierSkuVO();
        row.setSupplierId(supplierId);
        row.setSkuId(100L);
        row.setSupplierNameSnapshot("供货商" + supplierId);
        row.setPurchaseUnit(unit);
        row.setReferencePrice(referencePrice == null ? null : new BigDecimal(referencePrice));
        row.setDefaultFlag(false);
        return row;
    }
}
