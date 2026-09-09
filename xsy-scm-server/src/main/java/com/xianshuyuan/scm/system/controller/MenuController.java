package com.xianshuyuan.scm.system.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.system.dto.*;
import com.xianshuyuan.scm.system.service.MenuService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
@Validated
public class MenuController {
    private final MenuService service;
    @GetMapping
    public ApiResponse<PageData<MenuResponse>> list(@Valid MenuQuery query) { return ApiResponse.success(service.list(query)); }
    @GetMapping("/tree")
    public ApiResponse<List<MenuResponse>> tree(@Valid MenuTreeQuery query) { return ApiResponse.success(service.tree(query)); }
    @GetMapping("/{id}")
    public ApiResponse<MenuResponse> detail(@PathVariable @Positive long id) { return ApiResponse.success(service.detail(id)); }
    @PostMapping
    public ApiResponse<MenuResponse> create(@RequestBody @Valid CreateMenuRequest request, Authentication actor) { return ApiResponse.success(service.create(request, actor)); }
    @PutMapping("/{id}")
    public ApiResponse<MenuResponse> update(@PathVariable @Positive long id, @RequestBody @Valid UpdateMenuRequest request, Authentication actor) { return ApiResponse.success(service.update(id, request, actor)); }
    @PostMapping("/{id}/status")
    public ApiResponse<MenuResponse> status(@PathVariable @Positive long id, @RequestBody @Valid MenuStatusRequest request, Authentication actor) { return ApiResponse.success(service.changeStatus(id, request, actor)); }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive long id, @RequestParam @PositiveOrZero int version, Authentication actor) { service.delete(id, version, actor); return ApiResponse.success(null); }
}
