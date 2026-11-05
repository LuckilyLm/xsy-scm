package com.xsy.scm.sorting;

import com.xsy.scm.sorting.domain.entity.SortingTaskItemEntity;
import com.xsy.scm.sorting.domain.vo.SortingPartRecordVO;
import com.xsy.scm.sorting.service.SortingStandardPolicy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SortingStandardPolicyTest {
    private final SortingStandardPolicy policy = new SortingStandardPolicy();

    @Test
    void frozenStandardAllowsFractionalSaleUnitsButRejectsInvalidPlans() {
        var row = row();
        assertThat(policy.rejection(row, null)).isNull();
        for (String value : new String[] {"0", "-1", "1.00001", "100000000000000"}) {
            row.setPlannedQuantitySnapshot(new BigDecimal(value));
            assertThat(policy.rejection(row, null)).isNotBlank();
        }
        row.setPlannedQuantitySnapshot(null);
        assertThat(policy.rejection(row, null)).isNotBlank();
        row = row();
        row.setSaleUnitSnapshot(" ");
        assertThat(policy.rejection(row, null)).isNotBlank();
    }

    @Test
    void existingFactsAreNotSilentlyOverwritten() {
        var row = row();
        var previous = new SortingPartRecordVO();
        previous.setTotalQuantity(new BigDecimal("2.5000"));
        assertThat(policy.rejection(row, previous)).isNull();
        previous.setTotalQuantity(new BigDecimal("2.4"));
        assertThat(policy.rejection(row, previous)).contains("分次累计量");
        row.setResult("NORMAL");
        assertThat(policy.rejection(row, null)).contains("已确认");
        row = row();
        row.setProductTypeSnapshot("NON_STANDARD");
        assertThat(policy.rejection(row, null)).contains("仅支持标准品");
        row.setProductTypeSnapshot(null);
        assertThat(policy.rejection(row, null)).isNotBlank();
        row = row();
        row.setOccupationStatus("RELEASED");
        assertThat(policy.rejection(row, null)).contains("已释放");
    }

    private SortingTaskItemEntity row() {
        var row = new SortingTaskItemEntity();
        row.setOccupationStatus("ACTIVE");
        row.setProductTypeSnapshot("STANDARD");
        row.setSaleUnitSnapshot("箱");
        row.setPlannedQuantitySnapshot(new BigDecimal("2.5000"));
        return row;
    }
}
