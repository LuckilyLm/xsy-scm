package com.xsy.scm.report;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.xsy.scm.report.job.PurchaseDailyReportJob;
import com.xsy.scm.report.service.PurchaseDailyGenerationService;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PurchaseDailyReportJobTest {
    private final PurchaseDailyGenerationService generationService = mock(PurchaseDailyGenerationService.class);
    private final PurchaseDailyReportJob job = new PurchaseDailyReportJob(generationService);

    @Test
    void emptyParameterTargetsPreviousShanghaiDay() {
        LocalDate yesterday = LocalDate.now(ScmReportTimeRangeResolver.BUSINESS_ZONE).minusDays(1);
        when(generationService.generate(yesterday)).thenReturn(true);

        assertThat(job.run(null)).contains(yesterday.toString(), "已生成");
        verify(generationService).generate(yesterday);
    }

    @Test
    void manualDateDoesNotOverwriteExistingSnapshot() {
        LocalDate date = LocalDate.of(2020, 1, 2);
        when(generationService.generate(date)).thenReturn(false);

        assertThat(job.run(" 2020-01-02 ")).contains("已存在", "保留原始快照");
        verify(generationService).generate(date);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2020-02-30", "2020/01/02", "yesterday", "2020-1-2"})
    void malformedBackfillDateFailsInsteadOfSilentlyGeneratingYesterday(String parameter) {
        assertThatThrownBy(() -> job.run(parameter)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yyyy-MM-dd");
        verifyNoInteractions(generationService);
    }
}
