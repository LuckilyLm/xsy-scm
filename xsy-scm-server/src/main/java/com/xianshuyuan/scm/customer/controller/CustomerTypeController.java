package com.xianshuyuan.scm.customer.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.customer.dto.CustomerTypeSaveRequest;
import com.xianshuyuan.scm.customer.service.CustomerService;
import com.xianshuyuan.scm.customer.vo.CustomerTypeResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer-types")
public class CustomerTypeController {
    private final CustomerService service;

    public CustomerTypeController(CustomerService s) {
        service = s;
    }

    @GetMapping
    public ApiResponse<List<CustomerTypeResponse>> list() {
        return ApiResponse.success(service.listTypes().stream().map(e -> new CustomerTypeResponse(e.getId(), e.getVersion(), e.getTypeCode(), e.getName(), e.getStatus())).toList());
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody CustomerTypeSaveRequest r) {
        return ApiResponse.success(service.createType(r));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @Valid @RequestBody CustomerTypeSaveRequest r) {
        service.updateType(id, r);
        return ApiResponse.success(null);
    }
}
