package com.xsy.scm.admin.module.business.trace.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceInspectAddForm;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceInspectQueryForm;
import com.xsy.scm.admin.module.business.trace.domain.vo.TraceInspectVO;
import com.xsy.scm.admin.module.business.trace.service.TraceInspectService;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检测报告 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = "溯源管理-检测报告")
public class TraceInspectController {

    @Resource
    private TraceInspectService traceInspectService;

    @Operation(summary = "分页查询检测报告 @author xsy-scm")
    @PostMapping("/trace/inspect/query")
    @SaCheckPermission("traceInspect:query")
    public ResponseDTO<PageResult<TraceInspectVO>> query(@RequestBody @Valid TraceInspectQueryForm queryForm) {
        return traceInspectService.query(queryForm);
    }

    @Operation(summary = "添加检测报告 @author xsy-scm")
    @PostMapping("/trace/inspect/add")
    @SaCheckPermission("traceInspect:add")
    public ResponseDTO<String> add(@RequestBody @Valid TraceInspectAddForm addForm) {
        return traceInspectService.add(addForm);
    }

    @Operation(summary = "作废检测报告 @author xsy-scm")
    @PostMapping("/trace/inspect/invalidate/{inspectId}")
    @SaCheckPermission("traceInspect:invalidate")
    public ResponseDTO<String> invalidate(@PathVariable Long inspectId) {
        return traceInspectService.invalidate(inspectId);
    }

    @Operation(summary = "删除检测报告 @author xsy-scm")
    @GetMapping("/trace/inspect/delete/{inspectId}")
    @SaCheckPermission("traceInspect:delete")
    public ResponseDTO<String> delete(@PathVariable Long inspectId) {
        return traceInspectService.delete(inspectId);
    }

    @Operation(summary = "批量删除检测报告 @author xsy-scm")
    @PostMapping("/trace/inspect/batchDelete")
    @SaCheckPermission("traceInspect:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return traceInspectService.batchDelete(idList);
    }
}
