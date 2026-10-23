package com.xsy.scm.balance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.balance.domain.entity.CustomerBalanceRechargeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CustomerBalanceRechargeDao extends BaseMapper<CustomerBalanceRechargeEntity> {

    CustomerBalanceRechargeEntity selectByIdForUpdate(@Param("rechargeId") Long rechargeId);

    /** 充值单号序列。必须在事务内调用（nextval 不回滚，跳号可接受）。 */
    @org.apache.ibatis.annotations.Select("SELECT nextval('customer_balance_recharge_seq')")
    long nextRechargeNo();
}
