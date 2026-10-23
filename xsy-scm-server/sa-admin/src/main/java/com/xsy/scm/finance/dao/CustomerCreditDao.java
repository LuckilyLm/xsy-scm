package com.xsy.scm.finance.dao;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.domain.dto.FinanceCreditExposureDto;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CustomerCreditDao {
    FinanceCreditExposureDto selectExposure(@Param("settlementCustomerId") Long settlementCustomerId,
        @Param("scope") ScmValueScope scope);

    /**
     * 该结算主体「已确认但还没开应收」的订单主键。
     *
     * <p>
     * 占用金额不在这条 SQL 里算：它必须与签收应收同口径（实重 × 锁定价 − 冻结优惠等比分摊）， 那段折算在 {@code FinanceOrderPayableQueryService} 里，SQL
     * 里再写一遍就是第二套口径。
     */
    List<Long> selectConfirmedOrderIdsWithoutReceivable(@Param("settlementCustomerId") Long settlementCustomerId);
}
