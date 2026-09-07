package com.xianshuyuan.scm.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.order.entity.SalesOrderItemEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SalesOrderItemMapper extends BaseMapper<SalesOrderItemEntity> {
    List<SalesOrderItemEntity> selectActiveByOrderId(@Param("orderId") long orderId);

    List<SalesOrderItemEntity> selectActiveByOrderIds(@Param("orderIds") List<Long> orderIds);

    List<SalesOrderItemEntity> selectActiveByOrderIdForUpdate(@Param("orderId") long orderId);

    int softDeleteByIds(@Param("orderId") long orderId, @Param("ids") List<Long> ids);
}
