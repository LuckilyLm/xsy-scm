package com.xianshuyuan.scm.system.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.service.AuditLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class AuditLogController {
    private final AuditLogQueryService logs;

    @GetMapping("/login-logs")
    @Operation(summary = "分页查询登录日志", description = "需要 system:login-log:list；时间筛选采用带时区的 ISO 8601 格式，包含边界。")
    public ApiResponse<PageData<LoginLogResponse>> loginLogs(@Valid LoginLogQuery query) {
        return ApiResponse.success(logs.loginLogs(query));
    }

    @GetMapping("/operation-logs")
    @Operation(summary = "分页查询系统操作日志", description = "需要 system:operation-log:list；仅返回审计摘要，不返回原始请求和变更 JSON。")
    public ApiResponse<PageData<OperationLogResponse>> operationLogs(@Valid OperationLogQuery query) {
        return ApiResponse.success(logs.operationLogs(query));
    }
}
