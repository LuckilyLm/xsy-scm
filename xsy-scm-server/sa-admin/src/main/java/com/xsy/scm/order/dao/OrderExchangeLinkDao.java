package com.xsy.scm.order.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderExchangeLinkDao {
    /**
     * 只返回仍有效的换货单：关联的草稿被删除后，这条关联不再代表任何可履约的换货。
     */
    Long replacementOrderId(@Param("returnId") Long returnId);

    int insert(@Param("returnId") Long returnId, @Param("originalOrderId") Long originalOrderId,
        @Param("replacementOrderId") Long replacementOrderId, @Param("operator") String operator);

    /**
     * 换货草稿被删除时物理移除关联。关联只是关系记录；uk_order_exchange_return 是唯一索引，残留会挡住退货单重新创建换货。
     */
    int deleteByReplacementOrderId(@Param("replacementOrderId") Long replacementOrderId);
}
