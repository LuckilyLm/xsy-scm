package com.xsy.scm.trace;

import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.module.support.file.constant.FileRelationBizTypeEnum;
import com.xsy.scm.base.module.support.file.domain.vo.FileDownloadVO;
import com.xsy.scm.base.module.support.file.domain.vo.FileVO;
import com.xsy.scm.base.module.support.file.service.FileAccessGuard;
import com.xsy.scm.base.module.support.file.service.FileRelationService;
import com.xsy.scm.base.module.support.file.service.FileService;
import com.xsy.scm.common.ScmW3PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.trace.constant.ScmTraceInspectionConclusionEnum;
import com.xsy.scm.trace.constant.ScmTraceInspectionStatusEnum;
import com.xsy.scm.trace.dao.TraceInspectionReportDao;
import com.xsy.scm.trace.dao.TracePublicQueryDao;
import com.xsy.scm.trace.dao.TraceUnitDao;
import com.xsy.scm.trace.domain.form.TraceInspectionPhotoForm;
import com.xsy.scm.trace.domain.vo.TraceAttachmentVO;
import com.xsy.scm.trace.domain.vo.TraceInspectionReportVO;
import com.xsy.scm.trace.domain.vo.TraceUnitVO;
import com.xsy.scm.trace.service.TraceInspectionReportService;
import com.xsy.scm.trace.support.TracePublicRateLimiter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.xsy.scm.trace.service.TraceAttachmentService;
import com.xsy.scm.trace.service.TracePublicInspectionService;
import com.xsy.scm.trace.service.TracePublicService;

class TraceTest {

    @Nested
    class TraceUnitTest {
        @Nested

        class TraceInspectionPhotoServiceTest {
            private final TraceInspectionReportDao reports = mock(TraceInspectionReportDao.class);
            private final TraceUnitDao lots = mock(TraceUnitDao.class);
            private final ScmDataScopeService scopes = mock(ScmDataScopeService.class);
            private final ScmWarehouseScopeGuard warehouses = mock(ScmWarehouseScopeGuard.class);
            private final FileAccessGuard access = mock(FileAccessGuard.class);
            private final FileService files = mock(FileService.class);
            private final FileRelationService relations = mock(FileRelationService.class);
            private final TraceAttachmentService attachments = mock(TraceAttachmentService.class);
            private final TraceInspectionReportService service = new TraceInspectionReportService(reports, lots, scopes,
                warehouses, access, files, relations, attachments);

            @Test
            void photoUploadHasNoLotOrPublicationPrerequisiteAndDoesNotInventQualification() {
                var file = new FileVO();
                file.setFileName("市场检测.jpg");
                file.setFileSize(1024);
                file.setFileKey("private/common/photo.jpg");
                when(files.getFileMetadata(any())).thenReturn(List.of(file));
                when(reports.nextPhotoNo()).thenReturn(1L);
                doAnswer(call -> {
                    TraceInspectionReportVO report = call.getArgument(0);
                    report.setId(10L);
                    when(reports.lockPhoto(10L)).thenReturn(report);
                    return 1;
                }).when(reports).insert(any());
                var result = service.uploadPhotos(form(), "1:1");
                assertThat(result.getLotId()).isNull();
                assertThat(result.getInspectionDate()).isNull();
                assertThat(result.getInspectionOrg()).isNull();
                assertThat(result.getConclusion()).isEqualTo("UNDETERMINED");
                assertThat(result.getPublishedAt()).isNull();
                verify(access).checkRead(anyString(), any());
                verify(relations).rebind(FileRelationBizTypeEnum.TRACE_INSPECTION_REPORT, 10L, form().getFileKeys());
                verify(reports, never()).publish(any());
                verifyNoInteractions(lots, warehouses, scopes);
            }

            @Test
            void unreadableFileCannotCreateItsOwnAuthorizationRelation() {
                doThrow(new IllegalStateException("forbidden")).when(access).checkRead(anyString(), any());
                assertThatThrownBy(() -> service.uploadPhotos(form(), "1:1")).hasMessage("forbidden");
                verifyNoInteractions(reports, relations);
            }

            @Test
            void removalRevokesRelationAndRejectsStaleVersion() {
                var report = new TraceInspectionReportVO();
                report.setId(10L);
                report.setVersion(2);
                report.setStatus("DRAFT");
                when(reports.lockPhoto(10L)).thenReturn(report);
                when(reports.removePhoto(report)).thenReturn(1);
                assertThatThrownBy(() -> service.removePhotos(10L, 1, "1:1"))
                    .isInstanceOf(com.xsy.scm.common.exception.ScmBusinessException.class);
                verifyNoInteractions(relations);
                service.removePhotos(10L, 2, "1:1");
                verify(relations).rebind(FileRelationBizTypeEnum.TRACE_INSPECTION_REPORT, 10L, List.of());
            }

            private TraceInspectionPhotoForm form() {
                var form = new TraceInspectionPhotoForm();
                form.setFileKeys(List.of("private/common/photo.jpg"));
                return form;
            }
        }

        @Nested

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
    }

    @Nested
    @DisplayName("检测报告照片筛选（PG IT）")
    class TracePhotoQueryPgIT extends ScmW3PgITBase {
        @Autowired
        private TraceInspectionReportService reports;

        @Test
        @DisplayName("按上传日期与报告名筛选；开始日期晚于结束日期返回 41543")
        void filtersByUploadDateAndReportName() {
            insertPhoto("张三-十月蔬菜检测", "2026-10-01T10:00:00+08:00");
            insertPhoto("李四-十一月肉类检测", "2026-11-01T10:00:00+08:00");
            insertPhoto("王五-十月蔬菜检测", "2026-10-31T23:30:00+08:00");

            var october = reports.photos(null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null);
            assertThat(october).extracting(report -> report.getReportName())
                .containsExactlyInAnyOrder("张三-十月蔬菜检测", "王五-十月蔬菜检测");

            var byName = reports.photos(null, null, null, "李四");
            assertThat(byName).extracting(report -> report.getReportName()).containsExactly("李四-十一月肉类检测");

            assertThatThrownBy(() -> reports.photos(null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1), null))
                .isInstanceOf(ScmBusinessException.class)
                .extracting(error -> ((ScmBusinessException) error).getErrorCode().getCode()).isEqualTo(41543);
        }

        @Test
        void uploaderNameMatchesEvenWhenFileNameContainsNoName() {
            String name = "报告上传员" + UUID.randomUUID().toString().substring(0, 8);
            jdbc.update("UPDATE t_employee SET actual_name = ? WHERE employee_id = 1", name);
            insertPhoto("market-inspection-2026.jpg", "2026-11-01T10:00:00+08:00");
            assertThat(reports.photos(null, null, null, name)).extracting(report -> report.getReportName())
                .containsExactly("market-inspection-2026.jpg");
        }

        private void insertPhoto(String reportName, String createdAt) {
            jdbc.update("""
                    INSERT INTO xsy_trace_inspection_report (lot_id, report_no, report_name, inspection_date,
                        inspection_org, conclusion, created_by, updated_by, operation_history, created_at)
                    VALUES (NULL, ?, ?, NULL, NULL, 'UNDETERMINED', '1:1', '1:1', '[]'::jsonb, ?::timestamptz)
                    """, "PHOTO-IT-" + UUID.randomUUID(), reportName, createdAt);
        }
    }
}
