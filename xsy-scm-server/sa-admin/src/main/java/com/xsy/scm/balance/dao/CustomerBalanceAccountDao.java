package com.xsy.scm.balance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.balance.domain.entity.CustomerBalanceAccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CustomerBalanceAccountDao extends BaseMapper<CustomerBalanceAccountEntity> {

    int insertOnConflictDoNothing(CustomerBalanceAccountEntity account);

    CustomerBalanceAccountEntity selectBySettlementCustomerId(@Param("settlementCustomerId") Long settlementCustomerId);

    /**
     * 锁定余额账户，扣款链路的第一步。
     *
     * <p>
     * 并发余额支付先在这里串行：锁住账户后再汇总余额、判断够不够，否则两边都会读到「够」，各扣一次，把余额花成负数。
     */
    CustomerBalanceAccountEntity lockBySettlementCustomerId(@Param("settlementCustomerId") Long settlementCustomerId);
}
