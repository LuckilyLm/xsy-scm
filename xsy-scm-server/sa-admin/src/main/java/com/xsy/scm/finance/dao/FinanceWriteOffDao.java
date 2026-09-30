package com.xsy.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.time.ScmDateTimeRange;
import com.xsy.scm.finance.domain.entity.FinanceWriteOffEntity;
import com.xsy.scm.finance.domain.form.FinanceWriteOffQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 核销行读写。
 *
 * <p>
 * <b>撤销 = 新增 {@code REVERSE} 行</b>：本接口刻意不声明 update / delete。 {@code ck_finance_write_off_append_only} 拒绝软删，
 * {@code uk_finance_write_off_single_reverse} 保证一条 {@code NORMAL} 最多被反向一次 —— 重复撤销在库级失败，而不是靠服务层先查后判。
 *
 * <p>
 * 已核销额 / 未核销额 / 结清状态**一律读时派生**： 在此追加聚合查询， 但不得追加任何「把派生值写回本表或应收应付表」的方法（全局不变量 6）。
 */
@Mapper
public interface FinanceWriteOffDao extends BaseMapper<FinanceWriteOffEntity> {

    long nextWriteOffNo();

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
