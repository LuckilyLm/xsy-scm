package com.xianshuyuan.scm.system.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.service.UserRoleGrantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/users/{id}/roles")
@RequiredArgsConstructor
public class UserRoleGrantController {
    private final UserRoleGrantService grants;

    @GetMapping
    public ApiResponse<UserRolesResponse> read(@PathVariable @Positive long id) {
        return ApiResponse.success(grants.read(id));
    }

    @PostMapping
    public ApiResponse<UserRolesResponse> replace(@PathVariable @Positive long id,
                                                  @Valid @RequestBody ReplaceUserRolesRequest request, Authentication actor) {
        return ApiResponse.success(grants.replace(id, request, actor));
    }
}
