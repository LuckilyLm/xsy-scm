package com.xsy.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.common.time.ScmDateTimeRange;
import com.xsy.scm.finance.domain.dto.FinanceAutoWriteOffTargetDto;
import com.xsy.scm.finance.domain.dto.FinancePayableTargetDto;
import com.xsy.scm.finance.domain.entity.FinancePayableEntity;
import com.xsy.scm.finance.domain.form.FinancePayableQueryForm;
import com.xsy.scm.finance.domain.vo.FinancePayableVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 正常应付按业务来源防重；没有来源 ID 的手工红字应付用金额限制和请求幂等键防重。应付记录只能追加。
 */
@Mapper
public interface FinancePayableDao extends BaseMapper<FinancePayableEntity> {

    /**
     * 应付单号序列（全局非重置，不按日归零）。
     *
     * <p>
     * 必须在事务内调用：{@code nextval} 不随事务回滚，跳号是可接受的代价。
     */
    long nextPayableNo();

    /**
     * 插入正常应付，来源身份已存在时什么都不做。
     *
     * <p>
     * 冲突目标与 {@code uk_finance_payable_source_active} 的列和条件完全一致，不能用无目标的 {@code ON CONFLICT DO NOTHING}： 那样会把
     * {@code payable_no} 撞号也一起吞掉，单号异常将永远发现不了。
     *
     * @return 1 = 本次生成了应付（{@code id} 已回填）；0 = 该收货单已有应付，调用方按「已生成」成功返回
     */
    int insertOnConflictDoNothing(FinancePayableEntity entity);

    List<FinancePayableTargetDto> selectNormalTargetsForUpdate(@Param("targetIds") List<Long> targetIds);

    FinancePayableTargetDto selectNormalTargetForUpdate(@Param("targetId") Long targetId);

    /**
     * 自动核销候选：同一供应商的未结正常应付，按账单日期、id 升序（FIFO）。
     *
     * <p>
     * {@code scope} 为采购员数据范围，null 按失败关闭。
     */
    List<FinanceAutoWriteOffTargetDto> selectAutoTargets(@Param("supplierId") Long supplierId,
        @Param("scope") ScmValueScope scope, @Param("limit") int limit);

    BigDecimal selectRedAmount(@Param("originalPayableId") Long originalPayableId);

    FinancePayableEntity selectActiveById(@Param("payableId") Long payableId);

    FinancePayableVO selectSummaryById(@Param("payableId") Long payableId);

    List<FinancePayableVO> selectRedEntriesByOriginal(@Param("originalPayableId") Long originalPayableId);

    List<FinancePayableVO> queryPage(Page<?> page, @Param("query") FinancePayableQueryForm query,
        @Param("scope") ScmDataScopeContext scope, @Param("timeRange") ScmDateTimeRange timeRange);
}
