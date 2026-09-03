package com.xianshuyuan.scm.order.vo;
import com.xianshuyuan.scm.order.entity.*;
import java.time.OffsetDateTime;
import java.util.List;
public record SalesOrderResponse(Long id,Integer version,String orderNo,Long customerId,String customerName,OrderStatus status,OrderSource source,String supplementReason,Long originalOrderId,List<SalesOrderItemResponse> items,String totalAmount,OffsetDateTime createdAt,OffsetDateTime updatedAt) {}
