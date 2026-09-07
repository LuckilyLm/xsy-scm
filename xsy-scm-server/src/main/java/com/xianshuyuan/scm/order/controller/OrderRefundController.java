package com.xianshuyuan.scm.order.controller;

import com.xianshuyuan.scm.common.api.*;
import com.xianshuyuan.scm.order.dto.*;
import com.xianshuyuan.scm.order.entity.OrderRefundStatus;
import com.xianshuyuan.scm.order.service.*;
import com.xianshuyuan.scm.order.vo.OrderRefundResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order-refunds")
public class OrderRefundController {
    private final AfterSalesApplicationService app;
    private final AfterSalesQueryService query;

    public OrderRefundController(AfterSalesApplicationService a, AfterSalesQueryService q) {
        app = a;
        query = q;
    }

    @GetMapping
    public ApiResponse<PageData<OrderRefundResponse>> page(@RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) String keyword, @RequestParam(required = false) OrderRefundStatus status, @RequestParam(required = false) Long orderId, @RequestParam(required = false) Long returnId, @RequestParam(required = false) Long customerId) {
        return ApiResponse.success(query.pageRefunds(new OrderRefundPageQuery(page, pageSize, keyword, status, orderId, returnId, customerId)));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderRefundResponse> get(@PathVariable long id) {
        return ApiResponse.success(query.getRefund(id));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<Void> complete(@RequestHeader("Idempotency-Key") String key, @PathVariable long id, @Valid @RequestBody OrderRefundCompleteRequest r) {
        app.completeRefund(key, id, r);
        return ApiResponse.success(null);
    }
}
