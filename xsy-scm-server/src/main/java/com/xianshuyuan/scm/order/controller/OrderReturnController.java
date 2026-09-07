package com.xianshuyuan.scm.order.controller;

import com.xianshuyuan.scm.common.api.*;
import com.xianshuyuan.scm.order.dto.*;
import com.xianshuyuan.scm.order.entity.OrderReturnStatus;
import com.xianshuyuan.scm.order.service.*;
import com.xianshuyuan.scm.order.vo.OrderReturnResponse;
import jakarta.validation.Valid;

import java.util.List;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order-returns")
public class OrderReturnController {
    private final AfterSalesApplicationService app;
    private final AfterSalesQueryService query;

    public OrderReturnController(AfterSalesApplicationService a, AfterSalesQueryService q) {
        app = a;
        query = q;
    }

    @PostMapping
    public ApiResponse<Long> create(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody OrderReturnCreateRequest r) {
        return ApiResponse.success(app.create(key, r));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderReturnResponse> get(@PathVariable long id) {
        return ApiResponse.success(query.getReturn(id));
    }

    @GetMapping
    public ApiResponse<PageData<OrderReturnResponse>> page(@RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) String keyword, @RequestParam(required = false) OrderReturnStatus status, @RequestParam(required = false) Long orderId, @RequestParam(required = false) Long customerId) {
        return ApiResponse.success(query.pageReturns(new OrderReturnPageQuery(page, pageSize, keyword, status, orderId, customerId)));
    }

    @GetMapping("/by-order/{orderId}")
    public ApiResponse<List<OrderReturnResponse>> byOrder(@PathVariable long orderId) {
        return ApiResponse.success(query.byOrder(orderId));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Long> approve(@RequestHeader("Idempotency-Key") String key, @PathVariable long id, @Valid @RequestBody OrderReturnApproveRequest r) {
        return ApiResponse.success(app.approve(key, id, r));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Void> reject(@RequestHeader("Idempotency-Key") String key, @PathVariable long id, @Valid @RequestBody OrderReturnDecisionRequest r) {
        app.reject(key, id, r);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@RequestHeader("Idempotency-Key") String key, @PathVariable long id, @Valid @RequestBody OrderReturnDecisionRequest r) {
        app.cancel(key, id, r);
        return ApiResponse.success(null);
    }
}
