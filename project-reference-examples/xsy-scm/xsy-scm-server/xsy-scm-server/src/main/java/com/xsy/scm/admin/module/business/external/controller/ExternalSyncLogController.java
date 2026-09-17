package com.xsy.scm.admin.module.business.external.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.module.business.external.domain.form.ExternalSyncLogAddForm;
import com.xsy.scm.admin.module.business.external.domain.form.ExternalSyncLogQueryForm;
import com.xsy.scm.admin.module.business.external.domain.vo.ExternalSyncLogVO;
import com.xsy.scm.admin.module.business.external.service.ExternalSyncLogService;
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
 * 外部平台同步日志 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = "外部平台对接-同步日志")
public class ExternalSyncLogController {

    @Resource
    private ExternalSyncLogService externalSyncLogService;

    @Operation(summary = "分页查询外部平台同步日志 @author xsy-scm")
    @PostMapping("/external/syncLog/query")
    @SaCheckPermission("externalSyncLog:query")
    public ResponseDTO<PageResult<ExternalSyncLogVO>> query(@RequestBody @Valid ExternalSyncLogQueryForm queryForm) {
        return externalSyncLogService.query(queryForm);
    }

    @Operation(summary = "登记外部平台同步日志 @author xsy-scm")
    @PostMapping("/external/syncLog/add")
    @SaCheckPermission("externalSyncLog:add")
    public ResponseDTO<String> add(@RequestBody @Valid ExternalSyncLogAddForm addForm) {
        return externalSyncLogService.add(addForm);
    }

    @Operation(summary = "重试外部平台同步 @author xsy-scm")
    @PostMapping("/external/syncLog/retry/{logId}")
    @SaCheckPermission("externalSyncLog:retry")
    public ResponseDTO<String> retry(@PathVariable Long logId) {
        return externalSyncLogService.retry(logId);
    }

    @Operation(summary = "删除外部平台同步日志 @author xsy-scm")
    @GetMapping("/external/syncLog/delete/{logId}")
    @SaCheckPermission("externalSyncLog:delete")
    public ResponseDTO<String> delete(@PathVariable Long logId) {
        return externalSyncLogService.delete(logId);
    }

    @Operation(summary = "批量删除外部平台同步日志 @author xsy-scm")
    @PostMapping("/external/syncLog/batchDelete")
    @SaCheckPermission("externalSyncLog:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return externalSyncLogService.batchDelete(idList);
    }
}
