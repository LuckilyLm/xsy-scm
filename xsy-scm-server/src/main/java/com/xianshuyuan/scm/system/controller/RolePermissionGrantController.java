package com.xianshuyuan.scm.system.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.service.RolePermissionGrantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/roles/{id}/permissions")
@RequiredArgsConstructor
public class RolePermissionGrantController {
    private final RolePermissionGrantService grants;

    @GetMapping
    public ApiResponse<RolePermissionsResponse> read(@PathVariable @Positive long id) {
        return ApiResponse.success(grants.read(id));
    }

    @PostMapping
    public ApiResponse<RolePermissionsResponse> replace(@PathVariable @Positive long id,
                                                        @Valid @RequestBody ReplaceRolePermissionsRequest request, Authentication actor) {
        return ApiResponse.success(grants.replace(id, request, actor));
    }
}
