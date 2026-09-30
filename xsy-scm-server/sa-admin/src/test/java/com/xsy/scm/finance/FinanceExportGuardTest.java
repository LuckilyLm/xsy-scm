package com.xsy.scm.finance;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.finance.support.FinanceExportGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("财务同步导出行数守卫")
class FinanceExportGuardTest {

    @Test
    @DisplayName("完整结果在上限内返回，查询仅执行一次并探测多一行")
    void returnsRowsFromSingleProbeQuery() {
        AtomicLong requestedPageSize = new AtomicLong();
        List<String> expected = List.of("first", "second");

        List<String> actual = FinanceExportGuard.exportRows(pageSize -> {
            requestedPageSize.set(pageSize);
            return expected;
        });

        assertThat(actual).isSameAs(expected);
        assertThat(requestedPageSize).hasValue(FinanceExportGuard.PROBE_PAGE_SIZE);
    }

    @Test
    @DisplayName("超过十万行明确返回 41112，不静默截断")
    void rejectsRowsAboveLimit() {
        List<String> tooMany = Collections.nCopies((int) FinanceExportGuard.PROBE_PAGE_SIZE, "row");

        assertThatThrownBy(() -> FinanceExportGuard.exportRows(ignored -> tooMany))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        error -> assertThat(error.getErrorCode().getCode()).isEqualTo(41112));
    }
}
