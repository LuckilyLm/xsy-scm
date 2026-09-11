package com.xianshuyuan.scm.mall.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.mall.security.MallCustomerPrincipal;
import com.xianshuyuan.scm.mall.security.MallPrincipalResolver;
import com.xianshuyuan.scm.mall.service.MallCatalogService;
import com.xianshuyuan.scm.mall.vo.MallHomeResponse;
import com.xianshuyuan.scm.mall.vo.MallThemeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mall")
public class MallHomeController {

    private final MallCatalogService catalog;

    @GetMapping("/home")
    public ApiResponse<MallHomeResponse> home(Authentication authentication) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(catalog.home(principal.customerId()));
    }

    @GetMapping("/theme")
    public ApiResponse<MallThemeConfig> theme(Authentication authentication) {
        MallPrincipalResolver.require(authentication);
        return ApiResponse.success(catalog.theme());
    }
}
