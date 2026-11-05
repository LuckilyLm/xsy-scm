package com.xsy.scm.finance;

import com.xsy.scm.finance.support.FinanceExportLabels;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceExportLabelsTest {
    @Test
    void mapsReceiptsPaymentsAndWriteOffReversalsWithoutExposingInternalCodes() {
        assertThat(FinanceExportLabels.entryType("NORMAL")).isEqualTo("原始记录");
        assertThat(FinanceExportLabels.entryType("RED")).isEqualTo("扣减记录");
        assertThat(FinanceExportLabels.entryType("REVERSE")).isEqualTo("撤销记录");
        assertThat(FinanceExportLabels.entryType(null)).isEmpty();
        assertThat(FinanceExportLabels.entryType("")).isEmpty();
        assertThat(FinanceExportLabels.entryType("UNKNOWN")).isEqualTo("UNKNOWN");
    }
}
