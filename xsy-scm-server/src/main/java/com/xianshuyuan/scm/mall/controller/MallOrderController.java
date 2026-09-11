package com.xianshuyuan.scm.mall.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.mall.dto.MallCheckoutPreviewRequest;
import com.xianshuyuan.scm.mall.dto.MallOrderSubmitRequest;
import com.xianshuyuan.scm.mall.security.MallCustomerPrincipal;
import com.xianshuyuan.scm.mall.security.MallPrincipalResolver;
import com.xianshuyuan.scm.mall.service.MallCheckoutService;
import com.xianshuyuan.scm.mall.service.MallOrderService;
import com.xianshuyuan.scm.mall.vo.MallCheckoutPreviewResponse;
import com.xianshuyuan.scm.mall.vo.MallOrderResponse;
import com.xianshuyuan.scm.mall.vo.MallOrderSubmitResponse;
import com.xianshuyuan.scm.order.entity.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/mall/orders")
public class MallOrderController {

    private final MallCheckoutService checkout;
    private final MallOrderService orders;

    @PostMapping("/preview")
    public ApiResponse<MallCheckoutPreviewResponse> preview(Authentication authentication,
                                                            @Valid @RequestBody MallCheckoutPreviewRequest request) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(checkout.preview(principal.customerId(), request));
    }

    @PostMapping
    public ApiResponse<MallOrderSubmitResponse> submit(Authentication authentication,
                                                       @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                       @Valid @RequestBody MallOrderSubmitRequest request) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(checkout.submit(principal.customerId(), request, idempotencyKey));
    }

    @GetMapping
    public ApiResponse<PageData<MallOrderResponse>> page(
            Authentication authentication,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) long pageSize,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(orders.page(principal.customerId(), page, pageSize, status, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<MallOrderResponse> detail(Authentication authentication, @PathVariable long id) {
        MallCustomerPrincipal principal = MallPrincipalResolver.require(authentication);
        return ApiResponse.success(orders.detail(principal.customerId(), id));
    }
}
