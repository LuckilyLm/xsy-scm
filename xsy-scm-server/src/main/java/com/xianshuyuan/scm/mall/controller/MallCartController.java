package com.xianshuyuan.scm.mall.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.mall.dto.MallQuantityRequest;
import com.xianshuyuan.scm.mall.security.MallCustomerPrincipal;
import com.xianshuyuan.scm.mall.security.MallPrincipalResolver;
import com.xianshuyuan.scm.mall.service.MallCartService;
import com.xianshuyuan.scm.mall.vo.MallCartResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/mall/cart")
public class MallCartController {

    private final MallCartService cart;

    @GetMapping
    public ApiResponse<MallCartResponse> list(Authentication authentication) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(cart.list(principal.customerId()));
    }

    @PostMapping("/items")
    public ApiResponse<MallCartResponse> add(Authentication authentication,
                                             @Valid @RequestBody MallQuantityRequest request) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(cart.add(principal.customerId(), request.skuId(), request.quantity()));
    }

    @PutMapping("/items/{skuId}")
    public ApiResponse<MallCartResponse> update(Authentication authentication, @PathVariable long skuId,
                                                @Valid @RequestBody MallQuantityRequest request) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(cart.update(principal.customerId(), skuId, request.quantity()));
    }

    @DeleteMapping("/items/{skuId}")
    public ApiResponse<MallCartResponse> remove(Authentication authentication, @PathVariable long skuId) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(cart.remove(principal.customerId(), skuId));
    }
}
