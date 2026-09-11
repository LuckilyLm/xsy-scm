package com.xianshuyuan.scm.mall.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.mall.dto.MallProductPageQuery;
import com.xianshuyuan.scm.mall.security.MallCustomerPrincipal;
import com.xianshuyuan.scm.mall.security.MallPrincipalResolver;
import com.xianshuyuan.scm.mall.service.MallCatalogService;
import com.xianshuyuan.scm.mall.vo.MallCategoryResponse;
import com.xianshuyuan.scm.mall.vo.MallProductResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/mall/catalog")
public class MallCatalogController {

    private final MallCatalogService catalog;

    @GetMapping("/categories")
    public ApiResponse<List<MallCategoryResponse>> categories(Authentication authentication) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(catalog.categories(principal.customerId()));
    }

    @GetMapping("/products")
    public ApiResponse<PageData<MallProductResponse>> products(
            Authentication authentication,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        MallProductPageQuery query = new MallProductPageQuery(page, pageSize, keyword, categoryId,
                principal.customerId(), null);
        return ApiResponse.success(catalog.products(query));
    }

    @GetMapping("/products/{skuId}")
    public ApiResponse<MallProductResponse> product(Authentication authentication, @PathVariable long skuId) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(catalog.product(principal.customerId(), skuId));
    }
}
