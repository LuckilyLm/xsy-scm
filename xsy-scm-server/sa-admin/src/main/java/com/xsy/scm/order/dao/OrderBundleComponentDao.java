package com.xsy.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.order.domain.entity.OrderBundleComponentEntity;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderBundleComponentDao extends BaseMapper<OrderBundleComponentEntity> {
    List<OrderBundleComponentEntity> listByOrderItem(@Param("orderItemId") Long orderItemId);

    List<OrderBundleComponentEntity> listByOrder(@Param("orderId") Long orderId);

    List<OrderBundleComponentEntity> listByOrderItemIds(@Param("orderItemIds") List<Long> orderItemIds);
}
