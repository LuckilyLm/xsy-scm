package com.xianshuyuan.scm.purchase.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.purchase.dto.*;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderItemEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseOperationLogEntity;
import com.xianshuyuan.scm.purchase.mapper.PurchaseOrderItemMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseOperationLogMapper;
import com.xianshuyuan.scm.purchase.service.PurchaseOrderService;
import com.xianshuyuan.scm.purchase.vo.PurchaseOrderResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {
    private final PurchaseOrderService s;

    public PurchaseOrderController(PurchaseOrderService x) {
        s = x;
    }

    @GetMapping
    public ApiResponse<List<PurchaseOrderResponse>> list() {
        return ApiResponse.success(s.listResponses());
    }

    @GetMapping("/{id}")
    public ApiResponse<PurchaseOrderResponse> get(@PathVariable long id) {
        return ApiResponse.success(s.detail(id));
    }

    @GetMapping("/{id}/items")
    public ApiResponse<List<PurchaseOrderItemEntity>> items(@PathVariable long id) {
        return ApiResponse.success(s.items(id));
    }

    @GetMapping("/{id}/logs")
    public ApiResponse<List<PurchaseOperationLogEntity>> logs(@PathVariable long id) {
        return ApiResponse.success(s.logs(id));
    }

    @PostMapping
    public ApiResponse<Long> create(@RequestHeader("Idempotency-Key") String k, @Valid @RequestBody PurchaseOrderSaveRequest r) {
        return ApiResponse.success(s.create(r, k));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @Valid @RequestBody PurchaseOrderSaveRequest r) {
        s.update(id, r);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<Void> submit(@PathVariable long id, @RequestHeader("Idempotency-Key") String k, @Valid @RequestBody PurchaseOrderVersionRequest r) {
        s.submit(id, r.version(), k);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable long id, @RequestHeader("Idempotency-Key") String k, @Valid @RequestBody PurchaseOrderCancelRequest r) {
        s.cancel(id, r, k);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/short-close")
    public ApiResponse<Void> shortClose(@PathVariable long id, @RequestHeader("Idempotency-Key") String k,
            @Valid @RequestBody PurchaseOrderShortCloseRequest r) {
        s.shortClose(id, r, k);
        return ApiResponse.success(null);
    }
}
