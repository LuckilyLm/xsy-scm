package com.xsy.scm.finance.dao;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.time.ScmDateTimeRange;
import com.xsy.scm.finance.domain.entity.FinancePostingEntity;
import com.xsy.scm.finance.domain.entity.FinancePostingVoidEntity;
import com.xsy.scm.finance.domain.form.FinancePostingQueryForm;
import com.xsy.scm.finance.domain.vo.FinancePostingVO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 过账单据只追加与修改，不删除：登记错了走作废，作废是另一条事实。
 */
@Mapper
public interface FinancePostingDao extends BaseMapper<FinancePostingEntity> {

    /**
     * 过账单号序列（全局非重置）；{@code nextval} 不随事务回滚，跳号可接受。
     */
    long nextPostingNo();

    /**
     * 锁住过账行，让修改与作废在同行上串行。
     */
    FinancePostingEntity selectByIdForUpdate(@Param("postingId") Long postingId);

    /**
     * 已作废事实；没有则返回 {@code null}。{@code posting_id} 唯一，最多一行。
     */
    FinancePostingVoidEntity selectVoidByPostingId(@Param("postingId") Long postingId);

    /**
     * 作废事实只允许出现一次；并发重复作废由唯一索引挡回，返回 0 由调用方转成业务错误。
     */
    int insertVoidOnConflictDoNothing(FinancePostingVoidEntity entity);

    FinancePostingVO selectDetail(@Param("postingId") Long postingId, @Param("scope") ScmDataScopeContext scope);

    List<FinancePostingVO> queryPage(Page<?> page, @Param("query") FinancePostingQueryForm query,
        @Param("scope") ScmDataScopeContext scope, @Param("timeRange") ScmDateTimeRange timeRange);

    /**
     * 归属校验用：过账客户当前归属的业务员；客户不存在返回 {@code null}。
     */
    Long selectCustomerSellerId(@Param("postingId") Long postingId);
}
