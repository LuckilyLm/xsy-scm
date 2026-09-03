package com.xianshuyuan.scm.order.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.common.exception.ErrorCode;
import com.xianshuyuan.scm.order.entity.SalesOrderItemEntity;
import java.util.*;

public record SalesOrderItemChangeSet(List<SalesOrderItemEntity> inserted, List<SalesOrderItemEntity> updated, List<Long> removedIds) {
    private static final ErrorCode NOT_OWNED = new ErrorCode(40921, org.springframework.http.HttpStatus.CONFLICT, "订单行不属于当前订单");
    private static final ErrorCode VERSION_REQUIRED = new ErrorCode(40021, org.springframework.http.HttpStatus.BAD_REQUEST, "保留订单行必须携带版本");
    private static final ErrorCode VERSION_CONFLICT = new ErrorCode(40922, org.springframework.http.HttpStatus.CONFLICT, "订单行版本冲突");
    public SalesOrderItemChangeSet { inserted=List.copyOf(inserted); updated=List.copyOf(updated); removedIds=List.copyOf(removedIds); }
    public static SalesOrderItemChangeSet between(long orderId, List<SalesOrderItemEntity> existing, List<SalesOrderItemEntity> requested) {
        Map<Long,SalesOrderItemEntity> unmatched=new LinkedHashMap<>();
        existing.forEach(item -> { if (Objects.equals(orderId,item.getOrderId())) unmatched.put(item.getId(),item); });
        List<SalesOrderItemEntity> inserted=new ArrayList<>(), updated=new ArrayList<>();
        for (SalesOrderItemEntity item: requested) {
            if (item.getId()==null) { inserted.add(item); continue; }
            SalesOrderItemEntity persisted=unmatched.remove(item.getId());
            if (persisted==null) throw new BusinessException(NOT_OWNED);
            if (item.getVersion()==null) throw new BusinessException(VERSION_REQUIRED);
            if (!Objects.equals(item.getVersion(),persisted.getVersion())) throw new BusinessException(VERSION_CONFLICT);
            updated.add(item);
        }
        return new SalesOrderItemChangeSet(inserted,updated,new ArrayList<>(unmatched.keySet()));
    }
}
