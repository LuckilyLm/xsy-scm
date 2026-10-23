package com.xsy.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.time.ScmDateTimeRange;
import com.xsy.scm.finance.domain.entity.FinanceWriteOffEntity;
import com.xsy.scm.finance.domain.form.FinanceWriteOffQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffVO;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 核销记录只能新增。撤销核销须新增一条反向记录，不能删除旧核销；已核销金额与结清状态在查询时计算。
 */
@Mapper
public interface FinanceWriteOffDao extends BaseMapper<FinanceWriteOffEntity> {

    long nextWriteOffNo();

    FinanceWriteOffEntity selectNormalAllocation(@Param("sourceType") String sourceType,
        @Param("sourceId") Long sourceId, @Param("targetId") Long targetId);

    boolean hasReversal(@Param("writeOffId") Long writeOffId);

    int insertBalanceOnConflictDoNothing(FinanceWriteOffEntity entity);

    BigDecimal selectSourceUsedAmount(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    BigDecimal selectTargetWrittenOffAmount(@Param("targetType") String targetType, @Param("targetId") Long targetId);

    FinanceWriteOffEntity selectActiveById(@Param("writeOffId") Long writeOffId);

    FinanceWriteOffEntity selectByIdForUpdate(@Param("writeOffId") Long writeOffId);

    int insertReverseOnConflictDoNothing(FinanceWriteOffEntity entity);

    List<FinanceWriteOffVO> queryPage(Page<?> page, @Param("query") FinanceWriteOffQueryForm query,
        @Param("scope") ScmDataScopeContext scope, @Param("timeRange") ScmDateTimeRange timeRange);

    List<FinanceWriteOffVO> selectForTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId,
        @Param("scope") ScmDataScopeContext scope);

    List<FinanceWriteOffVO> selectForSource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId,
        @Param("scope") ScmDataScopeContext scope);
}
