package com.xianshuyuan.scm.supplier.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.supplier.dto.MasterDataStatusRequest;
import com.xianshuyuan.scm.supplier.dto.SupplierSaveRequest;
import com.xianshuyuan.scm.supplier.dto.SupplierSkuSaveRequest;
import com.xianshuyuan.scm.supplier.service.SupplierService;
import com.xianshuyuan.scm.supplier.service.SupplierSkuService;
import com.xianshuyuan.scm.supplier.vo.SupplierSkuVO;
import com.xianshuyuan.scm.supplier.vo.SupplierVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {
    private final SupplierService service;
    private final SupplierSkuService supplierSkus;

    public SupplierController(SupplierService service, SupplierSkuService supplierSkus) {
        this.service = service;
        this.supplierSkus = supplierSkus;
    }

    @GetMapping
    public ApiResponse<List<SupplierVO>> list() {
        return ApiResponse.success(service.listSuppliers());
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody SupplierSaveRequest request) {
        return ApiResponse.success(service.createSupplier(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @Valid @RequestBody SupplierSaveRequest request) {
        service.updateSupplier(id, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Void> status(@PathVariable long id, @Valid @RequestBody MasterDataStatusRequest request) {
        service.updateSupplierStatus(id, request.version(), request.status());
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}")
    public ApiResponse<SupplierVO> detail(@PathVariable long id) {
        return ApiResponse.success(service.supplierView(id));
    }

    @GetMapping("/{id}/skus")
    public ApiResponse<List<SupplierSkuVO>> skus(@PathVariable long id) {
        return ApiResponse.success(supplierSkus.listForSupplier(id));
    }

    @PutMapping("/{id}/skus")
    public ApiResponse<Void> replaceSkus(@PathVariable long id,
                                         @Valid @RequestBody List<SupplierSkuSaveRequest> requests) {
        supplierSkus.replaceForSupplier(id, requests);
        return ApiResponse.success(null);
    }
}
