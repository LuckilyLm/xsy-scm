package com.xianshuyuan.scm.mall.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.mall.dto.MallAddressSaveRequest;
import com.xianshuyuan.scm.mall.security.MallCustomerPrincipal;
import com.xianshuyuan.scm.mall.security.MallPrincipalResolver;
import com.xianshuyuan.scm.mall.service.MallAddressService;
import com.xianshuyuan.scm.mall.vo.MallAddressResponse;
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

import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/mall/addresses")
public class MallAddressController {

    private final MallAddressService addresses;

    @GetMapping
    public ApiResponse<List<MallAddressResponse>> list(Authentication authentication) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(addresses.list(principal.customerId()));
    }

    @PostMapping
    public ApiResponse<MallAddressResponse> create(Authentication authentication,
                                                   @Valid @RequestBody MallAddressSaveRequest request) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(addresses.create(principal.customerId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MallAddressResponse> update(Authentication authentication, @PathVariable long id,
                                                   @Valid @RequestBody MallAddressSaveRequest request) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(addresses.update(principal.customerId(), id, request));
    }

    @PostMapping("/{id}/default")
    public ApiResponse<MallAddressResponse> setDefault(Authentication authentication, @PathVariable long id) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(addresses.setDefault(principal.customerId(), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable long id) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        addresses.delete(principal.customerId(), id);
        return ApiResponse.success(null);
    }
}
