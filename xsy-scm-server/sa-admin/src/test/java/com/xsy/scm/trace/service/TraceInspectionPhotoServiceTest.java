package com.xsy.scm.trace.service;

import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.trace.dao.TraceInspectionReportDao;
import com.xsy.scm.trace.dao.TraceUnitDao;
import com.xsy.scm.trace.domain.form.TraceInspectionPhotoForm;
import com.xsy.scm.trace.domain.vo.TraceInspectionReportVO;
import java.util.List;
import net.lab1024.sa.base.module.support.file.constant.FileRelationBizTypeEnum;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import net.lab1024.sa.base.module.support.file.service.FileAccessGuard;
import net.lab1024.sa.base.module.support.file.service.FileRelationService;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
