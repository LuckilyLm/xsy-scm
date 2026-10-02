package com.xsy.scm.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.order.domain.entity.OrderReturnReceiptItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface OrderReturnReceiptItemDao extends BaseMapper<OrderReturnReceiptItemEntity> {
    BigDecimal receivedQuantity(@Param("returnItemId") Long returnItemId);
    BigDecimal receivedOrderItemQuantity(@Param("orderItemId") Long orderItemId);
    BigDecimal allocatedQuantity(@Param("sourceSalesOutMovementId") Long sourceSalesOutMovementId);
    List<OrderReturnReceiptItemEntity> listByReturnItemId(@Param("returnItemId") Long returnItemId);
    List<OrderReturnReceiptItemEntity> listByReturnId(@Param("returnId") Long returnId);
}
