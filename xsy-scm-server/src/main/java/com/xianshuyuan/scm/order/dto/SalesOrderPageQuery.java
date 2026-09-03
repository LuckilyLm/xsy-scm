package com.xianshuyuan.scm.order.dto;
import com.xianshuyuan.scm.order.entity.*;
public record SalesOrderPageQuery(long page,long pageSize,String keyword,OrderStatus status,Long customerId) {}
