package com.xsy.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.domain.vo.OrderRecentPriceVO;

@Mapper
public interface SalesOrderItemDao extends BaseMapper<SalesOrderItemEntity> {
    int softDelete(@Param("id") Long id, @Param("version") Integer version, @Param("operator") String operator);

    SalesOrderItemEntity lock(@Param("id") Long id);

    List<SalesOrderItemEntity> list(@Param("id") Long id);

    /**
     * 按订单批量读明细，供列表页一次取整页订单的行（避免 N+1）；顺序不保证，调用方按订单归组。
     */
    List<SalesOrderItemEntity> listByOrderIds(@Param("orderIds") List<Long> orderIds);

    /**
     * 某客户某 SKU 的最近已确认订单价（只读）：只取 CONFIRMED 订单行的锁定单价，按 {@code confirmed_at DESC, order_id DESC} 倒序， {@code limit} 限制的是最近
     * N 张订单而非行数。
     */
    List<OrderRecentPriceVO> recentPrices(@Param("customerId") Long customerId, @Param("skuId") Long skuId,
        @Param("limit") int limit);
}
