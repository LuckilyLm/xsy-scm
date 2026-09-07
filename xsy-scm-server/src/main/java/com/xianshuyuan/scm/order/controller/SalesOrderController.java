package com.xianshuyuan.scm.order.controller;

import com.xianshuyuan.scm.common.api.*;
import com.xianshuyuan.scm.order.dto.*;
import com.xianshuyuan.scm.order.entity.OrderStatus;
import com.xianshuyuan.scm.order.service.*;
import com.xianshuyuan.scm.order.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/orders")
public class SalesOrderController {
    private final SalesOrderQueryService query;
    private final SalesOrderApplicationService commands;

    public SalesOrderController(SalesOrderQueryService q, SalesOrderApplicationService c) {
        query = q;
        commands = c;
    }

    @GetMapping
    public ApiResponse<PageData<SalesOrderResponse>> page(@RequestParam(defaultValue = "1") @Min(1) long page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize, @RequestParam(required = false) String keyword, @RequestParam(required = false) OrderStatus status, @RequestParam(required = false) Long customerId) {
        return ApiResponse.success(query.page(new SalesOrderPageQuery(page, pageSize, keyword, status, customerId)));
    }

    @GetMapping("/{id}")
    public ApiResponse<SalesOrderResponse> get(@PathVariable long id) {
        return ApiResponse.success(query.get(id));
    }

    @GetMapping("/{id}/logs")
    public ApiResponse<List<OrderOperationLogResponse>> logs(@PathVariable long id) {
        return ApiResponse.success(query.logs(id));
    }

    @PostMapping
    public ApiResponse<Long> create(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody SalesOrderSaveRequest r) {
        return ApiResponse.success(commands.create(r, key));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @Valid @RequestBody SalesOrderSaveRequest r) {
        commands.update(id, r);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<SalesOrderResponse> submit(@PathVariable long id, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody OrderVersionRequest r) {
        return ApiResponse.success(commands.submit(id, r.version(), key));
    }

    @PostMapping("/{id}/items/{itemId}/actual-quantity")
    public ApiResponse<Void> actual(@PathVariable long id, @PathVariable long itemId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ActualQuantityRequest r) {
        commands.actualQuantity(id, itemId, r, key);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<Void> confirm(@PathVariable long id, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody OrderVersionRequest r) {
        commands.confirm(id, r.version(), key);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable long id, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CancelOrderRequest r) {
        commands.cancel(id, r, key);
        return ApiResponse.success(null);
    }
}
