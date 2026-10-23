package com.xsy.scm.mall.funds.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.finance.domain.vo.FinanceReceivableVO;
import com.xsy.scm.mall.funds.domain.vo.MallWalletMovementVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MallFundsSourceDao {
    boolean walletAccountExists(@Param("settlementCustomerId") Long settlementCustomerId);

    List<MallWalletMovementVO> walletMovements(Page<?> page, @Param("customerId") Long customerId,
        @Param("settlementCustomerId") Long settlementCustomerId, @Param("type") String type,
        @Param("direction") String direction);

    List<FinanceReceivableVO> receivables(Page<?> page, @Param("customerId") Long customerId,
        @Param("settleState") String settleState);

    FinanceReceivableVO receivable(@Param("customerId") Long customerId, @Param("receivableId") Long receivableId);

    CreditFacts creditFacts(@Param("customerId") Long customerId);

    @Data
    class CreditFacts {
        private BigDecimal openReceivableAmount;
        private BigDecimal confirmedOrderAmount;
        private LocalDate earliestOverdueDate;
    }
}
