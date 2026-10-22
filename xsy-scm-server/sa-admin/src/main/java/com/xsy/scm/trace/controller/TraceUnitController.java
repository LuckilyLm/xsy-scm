package com.xsy.scm.trace.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.trace.domain.form.TraceUnitBindForm;
import com.xsy.scm.trace.domain.form.TraceUnitIssueForm;
import com.xsy.scm.trace.domain.form.TraceUnitQueryForm;
import com.xsy.scm.trace.domain.form.TraceUnitVoidForm;
import com.xsy.scm.trace.domain.form.TraceUnitWeightForm;
import com.xsy.scm.trace.domain.vo.TraceUnitVO;
import com.xsy.scm.trace.permission.TracePermission;
import com.xsy.scm.trace.service.TraceUnitService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCM 包装码（一物一码）。
 *
 * <p>
 * 发码是唯一的写入口：码由系统生成、不透明、永不复用；回放只读，回答「这个码从哪来、到哪去」。 包装码与批次严格分层——批次码不冒充逐包装码。
 */
@RestController
@RequestMapping("/scm/inventory/unit")
@Tag(name = "SCM 包装码")
@RequiredArgsConstructor
public class TraceUnitController {

    private final TraceUnitService traceUnitService;

    @PostMapping("/issue")
    @SaCheckPermission(TracePermission.UNIT_ISSUE)
    public ResponseDTO<List<TraceUnitVO.Unit>> issue(@RequestBody @Valid TraceUnitIssueForm form) {
        return ResponseDTO.ok(traceUnitService.issue(form, ScmOperator.current()));
    }

    @PostMapping("/bind")
    @SaCheckPermission(TracePermission.UNIT_BIND)
    public ResponseDTO<TraceUnitVO.Binding> bind(@RequestBody @Valid TraceUnitBindForm form) {
        return ResponseDTO.ok(traceUnitService.bind(form, ScmOperator.current()));
    }

    @PostMapping("/void")
    @SaCheckPermission(TracePermission.UNIT_VOID)
    public ResponseDTO<String> voidUnit(@RequestBody @Valid TraceUnitVoidForm form) {
        traceUnitService.voidUnit(form, ScmOperator.current());
        return ResponseDTO.ok();
    }

    @PostMapping("/query")
    @SaCheckPermission(TracePermission.UNIT_QUERY)
    public ResponseDTO<PageResult<TraceUnitVO.Unit>> query(@RequestBody @Valid TraceUnitQueryForm form) {
        return ResponseDTO.ok(traceUnitService.query(form));
    }

    @GetMapping("/trace/{unitCode}")
    @SaCheckPermission(TracePermission.UNIT_QUERY)
    public ResponseDTO<TraceUnitVO.Replay> replay(@PathVariable String unitCode) {
        return ResponseDTO.ok(traceUnitService.replay(unitCode));
    }

    @PostMapping("/{unitCode}/weight")
    @SaCheckPermission(TracePermission.UNIT_WEIGH)
    public ResponseDTO<TraceUnitVO.Unit> weigh(@PathVariable String unitCode,
            @RequestBody @Valid TraceUnitWeightForm form) {
        return ResponseDTO.ok(traceUnitService.weigh(unitCode, form, ScmOperator.current()));
    }
}
