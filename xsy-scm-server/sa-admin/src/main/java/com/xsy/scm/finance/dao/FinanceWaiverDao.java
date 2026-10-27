package com.xsy.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.finance.domain.entity.FinanceWaiverEntity;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 抹零事实只追加、不修改：反向是追加一条 REVERSE 核销行，抹零表本身不留状态。
 */
@Mapper
public interface FinanceWaiverDao extends BaseMapper<FinanceWaiverEntity> {

    /**
     * 抹零单号序列（全局非重置，不按日归零）。必须在事务内调用：{@code nextval} 不随事务回滚，跳号可接受。
     */
    long nextWaiverNo();

    /**
     * 锁住抹零行，让反向在这行上串行执行。
     */
    FinanceWaiverEntity selectByIdForUpdate(@Param("waiverId") Long waiverId);
}
