package com.xianshuyuan.scm.system.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.service.RoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roles;

    @GetMapping
    public ApiResponse<PageData<RoleResponse>> list(@Valid @ModelAttribute RoleQuery query) {
        return ApiResponse.success(roles.list(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleResponse> detail(@PathVariable @Positive long id) {
        return ApiResponse.success(roles.detail(id));
    }

    @PostMapping
    public ApiResponse<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request, Authentication actor) {
        return ApiResponse.success(roles.create(request, actor));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoleResponse> update(@PathVariable @Positive long id,
                                            @Valid @RequestBody UpdateRoleRequest request, Authentication actor) {
        return ApiResponse.success(roles.update(id, request, actor));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<RoleResponse> status(@PathVariable @Positive long id,
                                            @Valid @RequestBody RoleStatusRequest request, Authentication actor) {
        return ApiResponse.success(roles.changeStatus(id, request, actor));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive long id,
                                    @RequestParam @PositiveOrZero int version, Authentication actor) {
        roles.delete(id, version, actor);
        return ApiResponse.success(null);
    }
}
