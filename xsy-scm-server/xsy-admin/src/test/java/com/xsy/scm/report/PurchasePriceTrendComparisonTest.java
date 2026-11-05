package com.xsy.scm.report;

import com.xsy.scm.report.domain.vo.PurchaseReportVO;
import com.xsy.scm.report.support.PurchasePriceTrendComparison;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchasePriceTrendComparisonTest {

    @Test
    void comparesPreviousValidDayOnlyWithinSameSkuAndPurchaseUnit() {
        var first = point("2026-10-01", 1L, "kg", "10.0000");
        var second = point("2026-10-03", 1L, "kg", "12.5000");
        var otherUnit = point("2026-10-04", 1L, "箱", "120.0000");
        var otherSku = point("2026-10-05", 2L, "kg", "20.0000");
        var third = point("2026-10-05", 1L, "kg", "10.0000");

        PurchasePriceTrendComparison.populate(new ArrayList<>(List.of(third, second, otherSku, first, otherUnit)));

        assertThat(first.getPreviousWeightedAvgPrice()).isNull();
        assertThat(second.getPreviousWeightedAvgPrice()).isEqualByComparingTo("10.0000");
        assertThat(second.getPriceChangeAmount()).isEqualByComparingTo("2.5000");
        assertThat(second.getPriceChangePercent()).isEqualByComparingTo("25.0000");
        assertThat(third.getPreviousWeightedAvgPrice()).isEqualByComparingTo("12.5000");
        assertThat(third.getPriceChangeAmount()).isEqualByComparingTo("-2.5000");
        assertThat(third.getPriceChangePercent()).isEqualByComparingTo("-20.0000");
        assertThat(otherUnit.getPriceChangePercent()).isNull();
        assertThat(otherSku.getPriceChangeAmount()).isNull();
    }

    @Test
    void zeroBasisKeepsAmountButPercentIsUnknown() {
        var zero = point("2026-10-01", 1L, "kg", "0.0000");
        var later = point("2026-10-02", 1L, "kg", "6.0000");

        PurchasePriceTrendComparison.populate(List.of(zero, later));

        assertThat(later.getPreviousWeightedAvgPrice()).isEqualByComparingTo("0");
        assertThat(later.getPriceChangeAmount()).isEqualByComparingTo("6.0000");
        assertThat(later.getPriceChangePercent()).isNull();
    }

    @Test
    void missingSampleDoesNotBecomeZeroOrBreakPriorValidObservation() {
        var first = point("2026-10-01", 1L, "kg", "8.0000");
        var missing = point("2026-10-02", 1L, "kg", null);
        var next = point("2026-10-03", 1L, "kg", "9.0000");

        PurchasePriceTrendComparison.populate(List.of(first, missing, next));

        assertThat(missing.getPriceChangeAmount()).isNull();
        assertThat(missing.getPreviousWeightedAvgPrice()).isNull();
        assertThat(next.getPreviousWeightedAvgPrice()).isEqualByComparingTo("8.0000");
        assertThat(next.getPriceChangePercent()).isEqualByComparingTo("12.5000");
    }

    @Test
    void reapplyingComparisonClearsStaleValuesAndEmptyInputIsAllowed() {
        assertThat(PurchasePriceTrendComparison.populate(List.of())).isEmpty();
        assertThat(PurchasePriceTrendComparison.populate(null)).isEmpty();
        var single = point("2026-10-01", 1L, "kg", "3.0000");
        single.setPriceChangeAmount(new BigDecimal("999.0000"));
        single.setPriceChangePercent(new BigDecimal("99.0000"));

        PurchasePriceTrendComparison.populate(List.of(single));

        assertThat(single.getPriceChangeAmount()).isNull();
        assertThat(single.getPriceChangePercent()).isNull();
    }

    private static PurchaseReportVO.PriceTrendPoint point(String date, Long skuId, String unit, String price) {
        var point = new PurchaseReportVO.PriceTrendPoint();
        point.setBizDate(date);
        point.setSkuId(skuId);
        point.setPurchaseUnit(unit);
        if (price != null) {
            point.setWeightedAvgPrice(new BigDecimal(price));
        }
        return point;
    }
}
