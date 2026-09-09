package com.xianshuyuan.scm.system.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.system.dto.RoleMenusResponse;
import com.xianshuyuan.scm.system.service.RoleMenuGrantService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/roles/{id}/menus")
@RequiredArgsConstructor
public class RoleMenuGrantController {
    private final RoleMenuGrantService grants;
    @PostMapping
    public ApiResponse<RoleMenusResponse> replace(@PathVariable @Positive long id,
            @jakarta.validation.Valid @RequestBody com.xianshuyuan.scm.system.dto.ReplaceRoleMenusRequest request,
            org.springframework.security.core.Authentication actor) {
        return ApiResponse.success(grants.replace(id, request, actor));
    }
    @GetMapping
    public ApiResponse<RoleMenusResponse> read(@PathVariable @Positive long id) {
        return ApiResponse.success(grants.read(id));
    }
}
