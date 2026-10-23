package com.xsy.scm.trace.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.trace.domain.form.TraceCorrectionApplyForm;
import com.xsy.scm.trace.domain.form.TraceCorrectionDecisionForm;
import com.xsy.scm.trace.domain.form.TraceCorrectionQueryForm;
import com.xsy.scm.trace.domain.vo.TraceCorrectionVO;
import com.xsy.scm.trace.permission.TracePermission;
import com.xsy.scm.trace.service.TraceCorrectionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCM 包装码更正。
 *
 * <p>
 * 解绑不是删除：申请 → 他人审批 → 追加解绑记录，三步都留痕；批准后包装码回到可绑定状态。 申请与审批分离，避免现场自己改自己绑错的码。
 */
@RestController
@RequestMapping("/scm/inventory/unit/correction")
@Tag(name = "SCM 包装码更正")
@RequiredArgsConstructor
public class TraceCorrectionController {

    private final TraceCorrectionService traceCorrectionService;

    @PostMapping("/apply")
    @SaCheckPermission(TracePermission.CORRECTION_APPLY)
    public ResponseDTO<TraceCorrectionVO> apply(@RequestBody @Valid TraceCorrectionApplyForm form) {
        return ResponseDTO.ok(traceCorrectionService.apply(form, ScmOperator.current()));
    }

    @PostMapping("/approve")
    @SaCheckPermission(TracePermission.CORRECTION_APPROVE)
    public ResponseDTO<TraceCorrectionVO> approve(@RequestBody @Valid TraceCorrectionDecisionForm form) {
        return ResponseDTO.ok(traceCorrectionService.approve(form, ScmOperator.current()));
    }

    @PostMapping("/reject")
    @SaCheckPermission(TracePermission.CORRECTION_APPROVE)
    public ResponseDTO<TraceCorrectionVO> reject(@RequestBody @Valid TraceCorrectionDecisionForm form) {
        return ResponseDTO.ok(traceCorrectionService.reject(form, ScmOperator.current()));
    }

    @PostMapping("/query")
    @SaCheckPermission(TracePermission.UNIT_QUERY)
    public ResponseDTO<PageResult<TraceCorrectionVO>> query(@RequestBody @Valid TraceCorrectionQueryForm form) {
        return ResponseDTO.ok(traceCorrectionService.query(form));
    }
}
