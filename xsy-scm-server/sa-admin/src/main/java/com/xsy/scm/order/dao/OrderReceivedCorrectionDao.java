package com.xsy.scm.order.dao;

import com.xsy.scm.order.domain.vo.OrderReceivedCorrectionVO;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderReceivedCorrectionDao {
    BigDecimal shippedQuantity(@Param("orderItemId") Long orderItemId);

    long signedAssignmentCount(@Param("orderId") Long orderId);

    OrderReceivedCorrectionVO latest(@Param("orderItemId") Long orderItemId);

    List<OrderReceivedCorrectionVO> list(@Param("orderId") Long orderId);

    int insert(@Param("correction") OrderReceivedCorrectionVO correction);
}
