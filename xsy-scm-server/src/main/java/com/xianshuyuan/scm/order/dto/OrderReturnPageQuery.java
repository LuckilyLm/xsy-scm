package com.xianshuyuan.scm.order.dto;
import com.xianshuyuan.scm.order.entity.OrderReturnStatus;
public record OrderReturnPageQuery(long page,long pageSize,String keyword,OrderReturnStatus status,Long orderId,Long customerId) {}
