package com.xsy.scm.admin.module.business.trace.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceBatchAddForm;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceBatchQueryForm;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceBatchUpdateForm;
import com.xsy.scm.admin.module.business.trace.domain.vo.TraceBatchVO;
import com.xsy.scm.admin.module.business.trace.service.TraceBatchService;
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
 * 溯源批次 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = "溯源管理-批次")
public class TraceBatchController {

    @Resource
    private TraceBatchService traceBatchService;

    @Operation(summary = "分页查询溯源批次 @author xsy-scm")
    @PostMapping("/trace/batch/query")
    @SaCheckPermission("traceBatch:query")
    public ResponseDTO<PageResult<TraceBatchVO>> query(@RequestBody @Valid TraceBatchQueryForm queryForm) {
        return traceBatchService.query(queryForm);
    }

    @Operation(summary = "添加溯源批次（生产批号） @author xsy-scm")
    @PostMapping("/trace/batch/add")
    @SaCheckPermission("traceBatch:add")
    public ResponseDTO<String> add(@RequestBody @Valid TraceBatchAddForm addForm) {
        return traceBatchService.add(addForm);
    }

    @Operation(summary = "更新溯源批次 @author xsy-scm")
    @PostMapping("/trace/batch/update")
    @SaCheckPermission("traceBatch:update")
    public ResponseDTO<String> update(@RequestBody @Valid TraceBatchUpdateForm updateForm) {
        return traceBatchService.update(updateForm);
    }

    @Operation(summary = "删除溯源批次 @author xsy-scm")
    @GetMapping("/trace/batch/delete/{batchId}")
    @SaCheckPermission("traceBatch:delete")
    public ResponseDTO<String> delete(@PathVariable Long batchId) {
        return traceBatchService.delete(batchId);
    }

    @Operation(summary = "批量删除溯源批次 @author xsy-scm")
    @PostMapping("/trace/batch/batchDelete")
    @SaCheckPermission("traceBatch:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return traceBatchService.batchDelete(idList);
    }
}
