package com.xianshuyuan.scm.purchase.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.purchase.dto.*;
import com.xianshuyuan.scm.purchase.entity.*;
import com.xianshuyuan.scm.purchase.service.PurchaseReceiptApplicationService;
import com.xianshuyuan.scm.purchase.vo.PurchaseReceiptConfirmResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-receipts")
public class PurchaseReceiptController {
    private final PurchaseReceiptApplicationService service;

    public PurchaseReceiptController(PurchaseReceiptApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<PurchaseReceiptEntity>> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<PurchaseReceiptEntity> get(@PathVariable long id) {
        return ApiResponse.success(service.require(id));
    }

    @GetMapping("/{id}/items")
    public ApiResponse<List<PurchaseReceiptItemEntity>> items(@PathVariable long id) {
        return ApiResponse.success(service.items(id));
    }

    @GetMapping("/{id}/confirmations")
    public ApiResponse<List<PurchaseReceiptConfirmationEntity>> confirmations(@PathVariable long id) {
        return ApiResponse.success(service.confirmations(id));
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody PurchaseReceiptCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<PurchaseReceiptConfirmResult> confirm(@PathVariable long id, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody PurchaseReceiptConfirmRequest request) {
        return ApiResponse.success(service.confirm(id, request, key));
    }

    @PostMapping("/{id}/putaway")
    public ApiResponse<PurchaseReceiptConfirmResult> putaway(@PathVariable long id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PurchaseReceiptPutawayRequest request) {
        return ApiResponse.success(service.putaway(id, request.version(), key));
    }
}
