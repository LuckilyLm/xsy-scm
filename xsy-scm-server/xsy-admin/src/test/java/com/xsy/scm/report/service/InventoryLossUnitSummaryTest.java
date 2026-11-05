package com.xsy.scm.report.service;

import com.xsy.scm.report.domain.vo.InventoryReportVO;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 损耗数量不得把公斤、箱和件相加，也不能混合盘亏与报损。 */
class InventoryLossUnitSummaryTest {

    @Test
    void formatsEachMovementTypeAndUnitIndependently() {
        var units = List.of(
                row("LOSS_REPORT", "箱", "2"),
                row("LOSS_REPORT", "kg", "12.5000"),
                row("STOCKTAKE_LOSS", "kg", "0.5000"));
        assertThat(InventoryReportService.lossQuantityText(units, "LOSS_REPORT"))
                .isEqualTo("2.0000 箱、12.5000 kg");
        assertThat(InventoryReportService.lossQuantityText(units, "STOCKTAKE_LOSS"))
                .isEqualTo("0.5000 kg");
        assertThat(InventoryReportService.lossQuantityText(units, "STOCKTAKE_GAIN")).isNull();
    }

    @Test
    void missingUnitIsNotSilentlyMergedWithKnownUnit() {
        assertThat(InventoryReportService.lossQuantityText(
                List.of(row("LOSS_REPORT", null, "3.0000")), "LOSS_REPORT"))
                .isEqualTo("3.0000 单位缺失");
    }

    private static InventoryReportVO.LossUnitRow row(String kind, String unit, String quantity) {
        var result = new InventoryReportVO.LossUnitRow();
        result.setMovementType(kind);
        result.setUnit(unit);
        result.setQuantity(new BigDecimal(quantity));
        return result;
    }
}
