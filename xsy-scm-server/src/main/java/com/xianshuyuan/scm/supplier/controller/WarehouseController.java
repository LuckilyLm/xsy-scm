package com.xianshuyuan.scm.supplier.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.supplier.dto.MasterDataStatusRequest;
import com.xianshuyuan.scm.supplier.dto.WarehouseSaveRequest;
import com.xianshuyuan.scm.supplier.service.WarehouseService;
import com.xianshuyuan.scm.supplier.vo.WarehouseVO;
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
@RequestMapping("/api/warehouses")
public class WarehouseController {
    private final WarehouseService service;

    public WarehouseController(WarehouseService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<WarehouseVO>> list() {
        return ApiResponse.success(service.listWarehouses());
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody WarehouseSaveRequest request) {
        return ApiResponse.success(service.createWarehouse(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable long id,
            @Valid @RequestBody WarehouseSaveRequest request
    ) {
        service.updateWarehouse(id, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Void> status(
            @PathVariable long id,
            @Valid @RequestBody MasterDataStatusRequest request
    ) {
        service.updateWarehouseStatus(id, request);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}")
    public ApiResponse<WarehouseVO> detail(@PathVariable long id) {
        return ApiResponse.success(service.warehouseView(id));
    }
}
