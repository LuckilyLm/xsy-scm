package com.xsy.scm.order.dao;

import com.xsy.scm.order.domain.dto.OrderRefundBalanceFact;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 只读查询余额返还状态；退款单不再另存一份资金状态。
 */
@Mapper
public interface OrderRefundFundingSourceDao {
    List<OrderRefundBalanceFact> selectBalanceReturns(@Param("refundIds") List<Long> refundIds);
}
