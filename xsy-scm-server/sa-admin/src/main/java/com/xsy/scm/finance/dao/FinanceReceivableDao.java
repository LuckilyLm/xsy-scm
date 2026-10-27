package com.xsy.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.common.time.ScmDateTimeRange;
import com.xsy.scm.finance.domain.dto.FinanceAutoWriteOffTargetDto;
import com.xsy.scm.finance.domain.dto.FinanceReceivableTargetDto;
import com.xsy.scm.finance.domain.entity.FinanceReceivableEntity;
import com.xsy.scm.finance.domain.form.FinanceReceivableQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceReceivableVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 应收记录只允许新增和查询，不修改、删除历史应收。红字冲回也新增记录；数据库约束和来源唯一索引防止重复生成。
 */
@Mapper
public interface FinanceReceivableDao extends BaseMapper<FinanceReceivableEntity> {

    /**
     * 应收单号序列（全局非重置，不按日归零）。
     *
     * <p>
     * 必须在事务内调用：{@code nextval} 不随事务回滚，跳号是可接受的代价。
     */
    long nextReceivableNo();

    /**
     * 插入正常应收，来源订单已生成过应收时什么都不做。
     *
     * <p>
     * 冲突目标与 {@code uk_finance_receivable_source_active} 的列和条件完全一致（本索引不含 {@code source_id IS NOT NULL}，因为应收来源恒非空）。
     *
     * @return 1 = 本次生成了应收（{@code id} 已回填）；0 = 该订单已有应收，调用方按「已生成」成功返回
     */
    int insertOnConflictDoNothing(FinanceReceivableEntity entity);

    /**
     * 退货红字应收必须关联原正常应收。正常应收的防重由数据库来源唯一索引承担；查询只是取得原单 ID，不代替防重。
     */
    FinanceReceivableEntity selectNormalByOrder(@Param("salesOrderId") Long salesOrderId);

    List<FinanceReceivableTargetDto> selectNormalTargetsForUpdate(@Param("targetIds") List<Long> targetIds);

    FinanceReceivableTargetDto selectNormalTargetForUpdate(@Param("targetId") Long targetId);

    /**
     * 自动核销候选：同一结算客户的未结正常应收，按账单日期、id 升序（FIFO）。
     *
     * <p>
     * 未结金额 = 应收金额 - 红字冲减 - 核销净额，现算；{@code scope} 为销售员数据范围，null 按失败关闭。
     */
    List<FinanceAutoWriteOffTargetDto> selectAutoTargets(@Param("settlementCustomerId") Long settlementCustomerId,
        @Param("scope") ScmValueScope scope, @Param("limit") int limit);

    BigDecimal selectRedAmount(@Param("originalReceivableId") Long originalReceivableId);

    FinanceReceivableEntity selectActiveById(@Param("receivableId") Long receivableId);

    FinanceReceivableVO selectSummaryById(@Param("receivableId") Long receivableId);

    List<FinanceReceivableVO> selectRedEntriesByOriginal(@Param("originalReceivableId") Long originalReceivableId);

    List<FinanceReceivableVO> queryPage(Page<?> page, @Param("query") FinanceReceivableQueryForm query,
        @Param("scope") ScmDataScopeContext scope, @Param("timeRange") ScmDateTimeRange timeRange);
}
