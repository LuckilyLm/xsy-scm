package com.xianshuyuan.scm.system.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.service.PermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/permissions")
@RequiredArgsConstructor
@Validated
public class PermissionController {
    private final PermissionService service;
    @GetMapping
    public ApiResponse<PageData<PermissionResponse>> list(@Valid PermissionQuery query) { return ApiResponse.success(service.list(query)); }
    @GetMapping("/{id}")
    public ApiResponse<PermissionResponse> detail(@PathVariable @Positive long id) { return ApiResponse.success(service.detail(id)); }
    @PostMapping
    public ApiResponse<PermissionResponse> create(@RequestBody @Valid CreatePermissionRequest request, Authentication actor) { return ApiResponse.success(service.create(request, actor)); }
    @PutMapping("/{id}")
    public ApiResponse<PermissionResponse> update(@PathVariable @Positive long id, @RequestBody @Valid UpdatePermissionRequest request, Authentication actor) { return ApiResponse.success(service.update(id, request, actor)); }
    @PostMapping("/{id}/status")
    public ApiResponse<PermissionResponse> status(@PathVariable @Positive long id, @RequestBody @Valid PermissionStatusRequest request, Authentication actor) { return ApiResponse.success(service.changeStatus(id, request, actor)); }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive long id, @RequestParam @PositiveOrZero int version, Authentication actor) { service.delete(id, version, actor); return ApiResponse.success(null); }
}
