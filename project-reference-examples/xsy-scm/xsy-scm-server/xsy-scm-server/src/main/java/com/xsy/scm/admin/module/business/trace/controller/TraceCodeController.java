package com.xsy.scm.admin.module.business.trace.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceCodeGenerateForm;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceCodeQueryForm;
import com.xsy.scm.admin.module.business.trace.domain.vo.TraceCodeVO;
import com.xsy.scm.admin.module.business.trace.service.TraceCodeService;
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
 * 溯源码 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = "溯源管理-溯源码")
public class TraceCodeController {

    @Resource
    private TraceCodeService traceCodeService;

    @Operation(summary = "分页查询溯源码 @author xsy-scm")
    @PostMapping("/trace/code/query")
    @SaCheckPermission("traceCode:query")
    public ResponseDTO<PageResult<TraceCodeVO>> query(@RequestBody @Valid TraceCodeQueryForm queryForm) {
        return traceCodeService.query(queryForm);
    }

    @Operation(summary = "生成溯源码（一码一批） @author xsy-scm")
    @PostMapping("/trace/code/generate")
    @SaCheckPermission("traceCode:generate")
    public ResponseDTO<String> generate(@RequestBody @Valid TraceCodeGenerateForm generateForm) {
        return traceCodeService.generate(generateForm);
    }

    @Operation(summary = "扫码查询溯源码 @author xsy-scm")
    @GetMapping("/trace/code/getByCode/{traceCode}")
    @SaCheckPermission("traceCode:query")
    public ResponseDTO<TraceCodeVO> getByCode(@PathVariable String traceCode) {
        return traceCodeService.getByCode(traceCode);
    }

    @Operation(summary = "作废溯源码 @author xsy-scm")
    @PostMapping("/trace/code/invalidate/{codeId}")
    @SaCheckPermission("traceCode:invalidate")
    public ResponseDTO<String> invalidate(@PathVariable Long codeId) {
        return traceCodeService.invalidate(codeId);
    }

    @Operation(summary = "删除溯源码 @author xsy-scm")
    @GetMapping("/trace/code/delete/{codeId}")
    @SaCheckPermission("traceCode:delete")
    public ResponseDTO<String> delete(@PathVariable Long codeId) {
        return traceCodeService.delete(codeId);
    }

    @Operation(summary = "批量删除溯源码 @author xsy-scm")
    @PostMapping("/trace/code/batchDelete")
    @SaCheckPermission("traceCode:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return traceCodeService.batchDelete(idList);
    }
}
