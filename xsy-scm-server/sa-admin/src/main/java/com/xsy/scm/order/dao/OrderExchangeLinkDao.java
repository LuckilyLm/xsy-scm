package com.xsy.scm.order.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderExchangeLinkDao {
    Long replacementOrderId(@Param("returnId") Long returnId);

    int insert(@Param("returnId") Long returnId, @Param("originalOrderId") Long originalOrderId,
            @Param("replacementOrderId") Long replacementOrderId, @Param("operator") String operator);
}
