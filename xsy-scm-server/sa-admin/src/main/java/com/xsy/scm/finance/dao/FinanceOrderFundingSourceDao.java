package com.xsy.scm.finance.dao;

import com.xsy.scm.balance.domain.entity.CustomerBalanceMovementEntity;
import com.xsy.scm.finance.domain.dto.FinanceOrderFundingDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 只读资金身份；只锁作为核销来源的余额流水，不锁订单或支付状态行。 */
@Mapper
public interface FinanceOrderFundingSourceDao {
    List<FinanceOrderFundingDto> selectOrderFunding(@Param("orderId") Long orderId);
    FinanceOrderFundingDto selectTransaction(@Param("transactionId") Long transactionId);
    CustomerBalanceMovementEntity lockMovement(@Param("movementId") Long movementId);
    boolean isRechargeReceipt(@Param("receiptId") Long receiptId);
}
