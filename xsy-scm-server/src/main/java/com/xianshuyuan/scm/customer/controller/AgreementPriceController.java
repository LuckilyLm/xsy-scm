package com.xianshuyuan.scm.customer.controller;

import com.xianshuyuan.scm.common.api.*;
import com.xianshuyuan.scm.customer.dto.AgreementPriceSaveRequest;
import com.xianshuyuan.scm.customer.service.AgreementPriceService;
import com.xianshuyuan.scm.customer.vo.AgreementPriceResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping({"/api/customer-agreement-prices", "/api/agreement-prices"})
public class AgreementPriceController {
    private final AgreementPriceService service;

    public AgreementPriceController(AgreementPriceService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageData<AgreementPriceResponse>> page(
        @RequestParam(defaultValue = "1") @Min(1) long page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
        @RequestParam(required = false) Long customerId,
        @RequestParam(required = false) Long skuId) {
        return ApiResponse.success(service.page(page, pageSize, customerId, skuId));
    }

    @PostMapping public ApiResponse<Long> create(@Valid @RequestBody AgreementPriceSaveRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}") public ApiResponse<Void> update(@PathVariable long id,
                                                         @Valid @RequestBody AgreementPriceSaveRequest request) {
        service.update(id, request);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable long id,
                                                             @RequestParam @Min(0) int version) {
        service.delete(id, version);
        return ApiResponse.success(null);
    }
}
