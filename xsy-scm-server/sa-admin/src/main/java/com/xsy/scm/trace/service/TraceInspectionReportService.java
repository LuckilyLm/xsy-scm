package com.xsy.scm.trace.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.trace.constant.TraceErrorCode;
import com.xsy.scm.trace.constant.ScmTraceInspectionStatusEnum;
import com.xsy.scm.trace.dao.TraceInspectionReportDao;
import com.xsy.scm.trace.dao.TraceUnitDao;
import com.xsy.scm.trace.domain.form.TraceInspectionReportSaveForm;
import com.xsy.scm.trace.domain.vo.TraceInspectionReportVO;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.module.support.file.constant.FileRelationBizTypeEnum;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import net.lab1024.sa.base.module.support.file.service.FileAccessGuard;
import net.lab1024.sa.base.module.support.file.service.FileRelationService;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TraceInspectionReportService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> FILE_TYPES = Set.of("pdf", "jpg", "jpeg", "png", "webp");

    private final TraceInspectionReportDao traceInspectionReportDao;
    private final TraceUnitDao traceUnitDao;
    private final ScmDataScopeService dataScopeService;
    private final ScmWarehouseScopeGuard warehouseScopeGuard;
    private final FileAccessGuard fileAccessGuard;
    private final FileService fileService;
    private final FileRelationService fileRelationService;
    private final TraceAttachmentService traceAttachmentService;

    @Transactional(readOnly = true)
    public List<TraceInspectionReportVO> list(Long lotId) {
        requireLot(lotId);
        List<TraceInspectionReportVO> reports = traceInspectionReportDao.listByLot(lotId,
            dataScopeService.resolve().getWarehouseScope());
        reports.forEach(this::fillAttachments);
        return reports;
    }

    @Transactional(rollbackFor = Exception.class)
    public TraceInspectionReportVO save(TraceInspectionReportSaveForm form, String operator) {
        requireLot(form.getLotId());
        validateDates(form.getInspectionDate(), form.getExpiresAt(), false);
        TraceInspectionReportVO report;
        if (form.getId() == null) {
            report = new TraceInspectionReportVO();
            report.setLotId(form.getLotId());
            report.setCreatedBy(operator);
        } else {
            report = lock(form.getId());
            requireState(report, form.getVersion(), ScmTraceInspectionStatusEnum.DRAFT);
            if (!Objects.equals(report.getLotId(), form.getLotId())) {
                throw new ScmBusinessException(TraceErrorCode.TRACE_REPORT_LOT_IMMUTABLE);
            }
        }
        List<String> keys = validateFiles(form.getFileKeys(), false);
        report.setReportNo(form.getReportNo().trim());
        report.setReportName(form.getReportName().trim());
        report.setInspectionDate(form.getInspectionDate());
        report.setInspectionOrg(form.getInspectionOrg().trim());
        report.setConclusion(form.getConclusion());
        report.setExpiresAt(form.getExpiresAt());
        report.setUpdatedBy(operator);
        try {
            if (report.getId() == null) {
                traceInspectionReportDao.insert(report);
            } else if (traceInspectionReportDao.updateDraft(report) != 1) {
                throw new ScmBusinessException(TraceErrorCode.TRACE_REPORT_STATE_INVALID);
            }
        } catch (DuplicateKeyException exception) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_REPORT_NO_EXISTS);
        }
        fileRelationService.rebind(FileRelationBizTypeEnum.TRACE_INSPECTION_REPORT, report.getId(), keys);
        TraceInspectionReportVO saved = lock(report.getId());
        fillAttachments(saved);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, Integer version, String operator) {
        TraceInspectionReportVO report = lock(id);
        requireState(report, version, ScmTraceInspectionStatusEnum.DRAFT);
        validateDates(report.getInspectionDate(), report.getExpiresAt(), true);
        validateFiles(traceAttachmentService.attachmentKeysOf(FileRelationBizTypeEnum.TRACE_INSPECTION_REPORT, id),
            true);
        report.setUpdatedBy(operator);
        if (traceInspectionReportDao.publish(report) != 1) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_REPORT_STATE_INVALID);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id, Integer version, String operator) {
        TraceInspectionReportVO report = lock(id);
        requireState(report, version, ScmTraceInspectionStatusEnum.PUBLISHED);
        report.setUpdatedBy(operator);
        if (traceInspectionReportDao.withdraw(report) != 1) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_REPORT_STATE_INVALID);
        }
    }

    private TraceInspectionReportVO lock(Long id) {
        TraceInspectionReportVO report = traceInspectionReportDao.lockById(id,
            dataScopeService.resolve().getWarehouseScope());
        if (report == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_REPORT_NOT_FOUND);
        }
        requireLot(report.getLotId());
        return report;
    }

    private void requireLot(Long lotId) {
        Long warehouseId = traceUnitDao.lotWarehouseId(lotId);
        if (warehouseId == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_LOT_NOT_FOUND);
        }
        warehouseScopeGuard.require(warehouseId);
        var receipt = traceUnitDao.receiptOfLot(lotId);
        if (receipt == null || receipt.getConfirmedAt() == null) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_LOT_NOT_FOUND);
        }
    }

    private void requireState(TraceInspectionReportVO report, Integer version, ScmTraceInspectionStatusEnum status) {
        if (!status.name().equals(report.getStatus()) || !Objects.equals(report.getVersion(), version)) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_REPORT_STATE_INVALID);
        }
    }

    private void validateDates(LocalDate date, OffsetDateTime expiresAt, boolean publishing) {
        if (date.isAfter(LocalDate.now(BUSINESS_ZONE))
            || (expiresAt != null && expiresAt.atZoneSameInstant(BUSINESS_ZONE).toLocalDate().isBefore(date))
            || (publishing && expiresAt != null && !expiresAt.isAfter(OffsetDateTime.now()))) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_REPORT_DATE_INVALID);
        }
    }

    private List<String> validateFiles(List<String> fileKeys, boolean publishing) {
        List<String> keys = fileKeys.stream().map(String::trim).distinct().toList();
        if (keys.size() > 20 || (publishing && keys.isEmpty())
            || keys.stream().anyMatch(key -> !key.startsWith("private/"))) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_REPORT_FILE_INVALID);
        }
        if (keys.isEmpty()) {
            return keys;
        }
        fileAccessGuard.checkRead(String.join(",", keys), SmartRequestUtil.getRequestUser());
        List<FileVO> files = fileService.getFileMetadata(keys);
        if (files.size() != keys.size() || files.stream().anyMatch(file -> !supportedFile(file))) {
            throw new ScmBusinessException(TraceErrorCode.TRACE_REPORT_FILE_INVALID);
        }
        return keys;
    }

    private boolean supportedFile(FileVO file) {
        String name = file.getFileName() == null ? "" : file.getFileName();
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return FILE_TYPES.contains(extension) && file.getFileSize() != null && file.getFileSize() > 0
            && file.getFileSize() <= 20 * 1024 * 1024;
    }

    private void fillAttachments(TraceInspectionReportVO report) {
        report.setAttachments(traceAttachmentService.reportAttachments(report.getId()));
        report.setExpired(report.getExpiresAt() != null && !report.getExpiresAt().isAfter(OffsetDateTime.now()));
    }
}
