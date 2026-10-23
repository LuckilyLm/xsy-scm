package com.xsy.scm.trace.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.product.permission.ProductPermission;
import com.xsy.scm.trace.domain.form.TraceAttachmentSaveForm;
import com.xsy.scm.trace.domain.vo.TraceAttachmentVO;
import com.xsy.scm.trace.permission.TracePermission;
import com.xsy.scm.trace.service.TraceAttachmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/**
 * SCM 溯源附件：商品资质与批次检测报告。
 *
 * <p>
 * 查看沿用被挂对象自身的权限（商品查看权 / 包装码查看权），只有写入单独收在 {@code scm:trace:file:manage}。 这样「能看这个商品」和「能改它的资质」是两件事，仓库岗不会因为要传检测报告而拿到商品写权限。
 */
@RestController
@RequestMapping("/scm/trace/attachment")
@Tag(name = "SCM 溯源附件")
@RequiredArgsConstructor
public class TraceAttachmentController {

    private final TraceAttachmentService traceAttachmentService;

    @GetMapping("/product/{spuId}")
    @SaCheckPermission(ProductPermission.QUERY)
    public ResponseDTO<List<TraceAttachmentVO>> productAttachments(@PathVariable Long spuId) {
        return ResponseDTO.ok(traceAttachmentService.productAttachments(spuId));
    }

    @PostMapping("/product/{spuId}/sync")
    @SaCheckPermission(TracePermission.FILE_MANAGE)
    public ResponseDTO<String> syncProductAttachments(@PathVariable Long spuId,
        @RequestBody @Valid TraceAttachmentSaveForm form) {
        traceAttachmentService.syncProductAttachments(spuId, form.getFileKeys());
        return ResponseDTO.ok();
    }

    @GetMapping("/lot/{lotId}")
    @SaCheckPermission(TracePermission.UNIT_QUERY)
    public ResponseDTO<List<TraceAttachmentVO>> lotAttachments(@PathVariable Long lotId) {
        return ResponseDTO.ok(traceAttachmentService.lotAttachments(lotId));
    }

    @PostMapping("/lot/{lotId}/sync")
    @SaCheckPermission(TracePermission.FILE_MANAGE)
    public ResponseDTO<String> syncLotAttachments(@PathVariable Long lotId,
        @RequestBody @Valid TraceAttachmentSaveForm form) {
        traceAttachmentService.syncLotAttachments(lotId, form.getFileKeys());
        return ResponseDTO.ok();
    }
}
