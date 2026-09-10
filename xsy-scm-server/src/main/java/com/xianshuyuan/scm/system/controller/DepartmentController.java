package com.xianshuyuan.scm.system.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.service.DepartmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/departments")
@RequiredArgsConstructor
public class DepartmentController {
    private final DepartmentService departments;

    @GetMapping
    public ApiResponse<PageData<DepartmentResponse>> list(@Valid @ModelAttribute DepartmentQuery query) {
        return ApiResponse.success(departments.list(query));
    }

    @GetMapping("/tree")
    public ApiResponse<List<DepartmentTreeResponse>> tree() {
        return ApiResponse.success(departments.tree());
    }

    @GetMapping("/{id}")
    public ApiResponse<DepartmentResponse> detail(@PathVariable @Positive long id) {
        return ApiResponse.success(departments.detail(id));
    }

    @GetMapping("/{id}/descendants")
    public ApiResponse<List<DepartmentResponse>> descendants(@PathVariable @Positive long id) {
        return ApiResponse.success(departments.descendants(id));
    }

    @PostMapping
    public ApiResponse<DepartmentResponse> create(@Valid @RequestBody CreateDepartmentRequest request, Authentication actor) {
        return ApiResponse.success(departments.create(request, actor));
    }

    @PutMapping("/{id}")
    public ApiResponse<DepartmentResponse> update(@PathVariable @Positive long id,
                                                  @Valid @RequestBody UpdateDepartmentRequest request, Authentication actor) {
        return ApiResponse.success(departments.update(id, request, actor));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<DepartmentResponse> status(@PathVariable @Positive long id,
                                                  @Valid @RequestBody DepartmentStatusRequest request, Authentication actor) {
        return ApiResponse.success(departments.changeStatus(id, request, actor));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive long id,
                                    @RequestParam @PositiveOrZero int version, Authentication actor) {
        departments.delete(id, version, actor);
        return ApiResponse.success(null);
    }
}
