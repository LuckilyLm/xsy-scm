package com.xianshuyuan.scm.customer.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.customer.dto.CustomerTypePriceSaveRequest;
import com.xianshuyuan.scm.customer.dto.CustomerTypePriceBatchRequest;
import com.xianshuyuan.scm.customer.service.CustomerTypePriceBatchService;
import com.xianshuyuan.scm.customer.service.CustomerTypePriceService;
import com.xianshuyuan.scm.customer.vo.CustomerTypePriceResponse;
import com.xianshuyuan.scm.customer.vo.CustomerTypePriceBatchResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/customer-type-prices")
public class CustomerTypePriceController {
    private final CustomerTypePriceService service;
    private final CustomerTypePriceBatchService batches;

    @GetMapping
    public ApiResponse<PageData<CustomerTypePriceResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) Long customerTypeId,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) @Size(max = 100) String keyword) {
        return ApiResponse.success(service.page(page, pageSize, customerTypeId, skuId, keyword));
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody CustomerTypePriceSaveRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PostMapping("/batch")
    public ApiResponse<CustomerTypePriceBatchResponse> batch(@Valid @RequestBody CustomerTypePriceBatchRequest request) {
        return ApiResponse.success(batches.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @Valid @RequestBody CustomerTypePriceSaveRequest request) {
        service.update(id, request);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id, @RequestParam @Min(0) int version) {
        service.delete(id, version);
        return ApiResponse.success(null);
    }
}
