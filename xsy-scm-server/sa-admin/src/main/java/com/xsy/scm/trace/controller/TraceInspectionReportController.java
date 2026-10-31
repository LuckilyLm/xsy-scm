package com.xsy.scm.trace.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.trace.domain.form.TraceInspectionReportActionForm;
import com.xsy.scm.trace.domain.form.TraceInspectionReportSaveForm;
import com.xsy.scm.trace.domain.form.TraceInspectionPhotoForm;
import org.springframework.web.bind.annotation.RequestParam;
import com.xsy.scm.trace.domain.vo.TraceInspectionReportVO;
import com.xsy.scm.trace.permission.TracePermission;
import com.xsy.scm.trace.service.TraceInspectionReportService;
import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/trace/inspection")
@RequiredArgsConstructor
public class TraceInspectionReportController {
    private final TraceInspectionReportService traceInspectionReportService;

    @GetMapping("/photos")
    @SaCheckPermission(TracePermission.PHOTO_QUERY)
    public ResponseDTO<List<TraceInspectionReportVO>> photos(@RequestParam(required = false) Long beforeId) {
        return ResponseDTO.ok(traceInspectionReportService.photos(beforeId));
    }

    @PostMapping("/photos")
    @SaCheckPermission(TracePermission.PHOTO_UPLOAD)
    public ResponseDTO<TraceInspectionReportVO> uploadPhotos(@Valid @RequestBody TraceInspectionPhotoForm form) {
        return ResponseDTO.ok(traceInspectionReportService.uploadPhotos(form, ScmOperator.current()));
    }

    @PostMapping("/photos/{id}/remove")
    @SaCheckPermission(TracePermission.PHOTO_REMOVE)
    public ResponseDTO<String> removePhotos(@PathVariable Long id,
        @Valid @RequestBody TraceInspectionReportActionForm form) {
        traceInspectionReportService.removePhotos(id, form.getVersion(), ScmOperator.current());
        return ResponseDTO.ok();
    }

    @GetMapping("/lot/{lotId}")
    @SaCheckPermission(value = {TracePermission.UNIT_QUERY, TracePermission.INSPECTION_MANAGE}, mode = SaMode.OR)
    public ResponseDTO<List<TraceInspectionReportVO>> list(@PathVariable Long lotId) {
        return ResponseDTO.ok(traceInspectionReportService.list(lotId));
    }

    @PostMapping("/save")
    @SaCheckPermission(TracePermission.INSPECTION_MANAGE)
    public ResponseDTO<TraceInspectionReportVO> save(@Valid @RequestBody TraceInspectionReportSaveForm form) {
        return ResponseDTO.ok(traceInspectionReportService.save(form, ScmOperator.current()));
    }

    @PostMapping("/{id}/publish")
    @SaCheckPermission(TracePermission.INSPECTION_MANAGE)
    public ResponseDTO<String> publish(@PathVariable Long id,
        @Valid @RequestBody TraceInspectionReportActionForm form) {
        traceInspectionReportService.publish(id, form.getVersion(), ScmOperator.current());
        return ResponseDTO.ok();
    }

    @PostMapping("/{id}/withdraw")
    @SaCheckPermission(TracePermission.INSPECTION_MANAGE)
    public ResponseDTO<String> withdraw(@PathVariable Long id,
        @Valid @RequestBody TraceInspectionReportActionForm form) {
        traceInspectionReportService.withdraw(id, form.getVersion(), ScmOperator.current());
        return ResponseDTO.ok();
    }
}
