package com.xsy.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.xsy.scm.order.domain.entity.OrderReturnItemEntity;

@Mapper
public interface OrderReturnItemDao extends BaseMapper<OrderReturnItemEntity> {
    OrderReturnItemEntity lock(@Param("id") Long id);

    List<OrderReturnItemEntity> list(@Param("id") Long id);

    /** 按退货单批量读明细：归属由调用方按退货单客户判定，本查询只按 return_id 等值过滤。 */
    List<OrderReturnItemEntity> listByReturnIds(@Param("returnIds") List<Long> returnIds);

    java.math.BigDecimal reserved(@Param("id") Long id);
}
