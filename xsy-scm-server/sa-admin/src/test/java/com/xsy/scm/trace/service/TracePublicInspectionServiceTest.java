package com.xsy.scm.trace.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.trace.constant.ScmTraceInspectionConclusionEnum;
import com.xsy.scm.trace.constant.ScmTraceInspectionStatusEnum;
import com.xsy.scm.trace.dao.TraceInspectionReportDao;
import com.xsy.scm.trace.dao.TracePublicQueryDao;
import com.xsy.scm.trace.dao.TraceUnitDao;
import com.xsy.scm.trace.domain.vo.TraceAttachmentVO;
import com.xsy.scm.trace.domain.vo.TraceInspectionReportVO;
import com.xsy.scm.trace.domain.vo.TraceUnitVO;
import com.xsy.scm.trace.support.TracePublicRateLimiter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.file.domain.vo.FileDownloadVO;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TracePublicInspectionServiceTest {
    private final TraceInspectionReportDao reports = mock(TraceInspectionReportDao.class);
    private final TraceAttachmentService attachments = mock(TraceAttachmentService.class);
    private final TracePublicInspectionService inspections = new TracePublicInspectionService(reports, attachments);
    private final TraceUnitDao units = mock(TraceUnitDao.class);
    private final FileService files = mock(FileService.class);
    private final TracePublicService trace = new TracePublicService(units, mock(TracePublicQueryDao.class),
            attachments, mock(TracePublicRateLimiter.class), files, inspections);

    @Test
    void expiredReportsKeepTheirHistoricalConclusionButDoNotResolveFiles() {
        TraceInspectionReportVO report = report(1L, "UNQUALIFIED", OffsetDateTime.now().minusDays(1));
        when(reports.publishedByLot(50L)).thenReturn(List.of(report));

        var result = inspections.reports(50L).get(0);
        assertThat(result.isExpired()).isTrue();
        assertThat(result.getConclusion()).isEqualTo("UNQUALIFIED");
        assertThat(result.getAttachments()).isEmpty();
        verify(attachments, never()).reportAttachments(any());
    }

    @Test
    void withdrawalRechecksPublicationOnTheSamePreviouslyValidDownloadLink() {
        activeUnit("token", 50L);
        when(reports.publishedByLot(50L)).thenReturn(
                List.of(report(1L, "QUALIFIED", OffsetDateTime.now().plusDays(1))), List.of());
        when(attachments.reportAttachments(1L)).thenReturn(List.of(attachment("private/common/report.pdf")));
        when(files.getDownloadFile("private/common/report.pdf", null)).thenReturn(ResponseDTO.ok(new FileDownloadVO()));

        assertThat(trace.file("token", "private/common/report.pdf", "127.0.0.1")).isNotNull();
        assertThatThrownBy(() -> trace.file("token", "private/common/report.pdf", "127.0.0.1"))
                .isInstanceOf(ScmBusinessException.class);
        verify(files).getDownloadFile("private/common/report.pdf", null);
    }

    @Test
    void tokenForAnotherLotCannotDownloadKnownReportKey() {
        activeUnit("other-token", 60L);
        when(reports.publishedByLot(60L)).thenReturn(List.of());

        assertThatThrownBy(() -> trace.file("other-token", "private/common/report.pdf", "127.0.0.1"))
                .isInstanceOf(ScmBusinessException.class);
        verify(files, never()).getDownloadFile(anyString(), isNull());
        verify(reports).publishedByLot(60L);
    }

    @Test
    void expiredReportCannotBeDownloadedByItsOwnLotToken() {
        activeUnit("token", 50L);
        when(reports.publishedByLot(50L)).thenReturn(
                List.of(report(1L, "QUALIFIED", OffsetDateTime.now().minusDays(1))));

        assertThatThrownBy(() -> trace.file("token", "private/common/report.pdf", "127.0.0.1"))
                .isInstanceOf(ScmBusinessException.class);
        verify(files, never()).getDownloadFile(anyString(), isNull());
    }

    private void activeUnit(String token, Long lotId) {
        TraceUnitVO.Unit unit = new TraceUnitVO.Unit();
        unit.setLotId(lotId);
        unit.setStatus("ACTIVE");
        when(units.findByPublicToken(token)).thenReturn(unit);
        when(attachments.publicAttachments(lotId, null)).thenReturn(List.of());
    }

    private TraceInspectionReportVO report(Long id, String conclusion, OffsetDateTime expiresAt) {
        TraceInspectionReportVO report = new TraceInspectionReportVO();
        report.setId(id);
        report.setLotId(50L);
        report.setReportNo("R-" + id);
        report.setReportName("检测报告");
        report.setInspectionDate(LocalDate.now().minusDays(2));
        report.setConclusion(ScmTraceInspectionConclusionEnum.valueOf(conclusion).name());
        report.setStatus(ScmTraceInspectionStatusEnum.PUBLISHED.name());
        report.setExpiresAt(expiresAt);
        return report;
    }

    private TraceAttachmentVO attachment(String key) {
        TraceAttachmentVO file = new TraceAttachmentVO();
        file.setFileKey(key);
        file.setFileName("检测报告.pdf");
        return file;
    }
}
