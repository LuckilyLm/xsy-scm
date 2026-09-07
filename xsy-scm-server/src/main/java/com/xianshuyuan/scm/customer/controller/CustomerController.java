package com.xianshuyuan.scm.customer.controller;

import com.xianshuyuan.scm.common.api.*;
import com.xianshuyuan.scm.customer.dto.*;
import com.xianshuyuan.scm.customer.service.*;
import com.xianshuyuan.scm.customer.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService service;
    private final CustomerQueryService query;
    private final OrderableSkuQueryService orderableSkus;

    public CustomerController(CustomerService service, CustomerQueryService query,
                              OrderableSkuQueryService orderableSkus) {
        this.service = service;
        this.query = query;
        this.orderableSkus = orderableSkus;
    }

    @GetMapping
    public ApiResponse<PageData<CustomerResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerTypeId) {
        return ApiResponse.success(query.page(new CustomerPageQuery(page, pageSize, keyword, customerTypeId)));
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> get(@PathVariable long id) {
        return ApiResponse.success(query.get(id));
    }

    @GetMapping("/{id}/skus")
    public ApiResponse<List<OrderableSkuResponse>> skus(@PathVariable long id) {
        return ApiResponse.success(orderableSkus.listOrderable(id));
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody CustomerSaveRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id,
                                    @Valid @RequestBody CustomerSaveRequest request) {
        service.update(id, request);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> status(@PathVariable long id,
                                    @Valid @RequestBody CustomerStatusRequest request) {
        service.updateStatus(id, request.version(), request.status());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id,
                                    @RequestParam @Min(0) int version) {
        service.delete(id, version);
        return ApiResponse.success(null);
    }
}
