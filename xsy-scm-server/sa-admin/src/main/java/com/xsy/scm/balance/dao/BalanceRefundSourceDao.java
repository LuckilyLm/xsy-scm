package com.xsy.scm.balance.dao;

import com.xsy.scm.balance.domain.dto.BalanceRefundFact;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 退款返还所需的跨域只读来源，订单锁串行同订单的消费及退款本金使用。 */
@Mapper
public interface BalanceRefundSourceDao {
    BalanceRefundFact selectRefund(@Param("refundId") Long refundId);
    Long lockOrder(@Param("orderId") Long orderId);
    List<Long> lockOrderRefunds(@Param("orderId") Long orderId);
    boolean hasCashRefund(@Param("orderId") Long orderId);
    boolean hasPendingFunding(@Param("orderId") Long orderId);
    boolean hasInvalidReturns(@Param("orderId") Long orderId);
    BigDecimal returnedAmount(@Param("orderId") Long orderId);
}
