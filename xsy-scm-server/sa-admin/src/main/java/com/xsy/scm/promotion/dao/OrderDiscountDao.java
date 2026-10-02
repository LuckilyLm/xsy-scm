package com.xsy.scm.promotion.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.promotion.domain.entity.OrderDiscountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单优惠冻结读写。
 *
 * <p>
 * 没有更新与删除：表上的触发器会拒绝改动金额与分摊，接口层也不提供 ——
 * 能改就等于退款时可以按新活动重算。
 */
@Mapper
public interface OrderDiscountDao extends BaseMapper<OrderDiscountEntity> {

    OrderDiscountEntity selectByOrderId(@Param("salesOrderId") Long salesOrderId);

    int insertDiscount(@Param("row") OrderDiscountEntity row);
}
