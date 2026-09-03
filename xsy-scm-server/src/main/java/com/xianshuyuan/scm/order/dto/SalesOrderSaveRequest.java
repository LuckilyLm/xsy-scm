package com.xianshuyuan.scm.order.dto;

import com.xianshuyuan.scm.order.entity.OrderSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record SalesOrderSaveRequest(
    @Min(value=0,message="版本号不正确") Integer version,
    @NotNull(message="客户不能为空") Long customerId,
    @NotNull(message="订单来源不能为空") OrderSource source,
    Long originalOrderId,
    String supplementReason,
    @NotEmpty(message="订单至少包含一行") List<@Valid SalesOrderItemSaveRequest> items
) {}
