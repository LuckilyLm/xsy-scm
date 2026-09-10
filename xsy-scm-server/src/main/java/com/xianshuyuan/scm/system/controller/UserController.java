package com.xianshuyuan.scm.system.controller;

import com.xianshuyuan.scm.auth.service.PasswordManagementService;
import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService users;
    private final PasswordManagementService passwords;

    @GetMapping
    public ApiResponse<PageData<UserResponse>> list(@Valid @ModelAttribute UserQuery query) {
        return ApiResponse.success(users.list(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> detail(@PathVariable @Positive long id) {
        return ApiResponse.success(users.detail(id));
    }

    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request, Authentication actor) {
        return ApiResponse.success(users.create(request, actor));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(@PathVariable @Positive long id,
                                            @Valid @RequestBody UpdateUserRequest request, Authentication actor) {
        return ApiResponse.success(users.update(id, request, actor));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<UserResponse> status(@PathVariable @Positive long id,
                                            @Valid @RequestBody UserStatusRequest request, Authentication actor) {
        return ApiResponse.success(users.changeStatus(id, request, actor));
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable @Positive long id,
                                           @Valid @RequestBody ResetPasswordRequest request, Authentication actor) {
        passwords.reset(id, request, actor);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive long id,
                                    @RequestParam @PositiveOrZero int version, Authentication actor) {
        users.delete(id, version, actor);
        return ApiResponse.success(null);
    }
}
