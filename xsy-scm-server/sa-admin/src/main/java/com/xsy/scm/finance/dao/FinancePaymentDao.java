package com.xsy.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.time.ScmDateTimeRange;
import com.xsy.scm.finance.domain.entity.FinancePaymentEntity;
import com.xsy.scm.finance.domain.form.FinancePaymentQueryForm;
import com.xsy.scm.finance.domain.vo.FinancePaymentQueryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 财务付款只追加不修改，退款付款以退款单来源唯一索引防重；反向付款无原业务来源 ID，不占用该索引。
 */
@Mapper
public interface FinancePaymentDao extends BaseMapper<FinancePaymentEntity> {

    /**
     * 付款单号序列（全局非重置，不按日归零）。必须在事务内调用：{@code nextval} 不随事务回滚，跳号是可接受的代价（与应付、收款单号一致）。
     */
    long nextPaymentNo();

    /**
     * 插入一笔正常付款，同一 {@code ORDER_REFUND} 已有付款时什么都不做。
     *
     * <p>
     * 冲突目标与 {@code uk_finance_payment_source_active} 的列和条件完全一致：少写条件会命中「无索引可仲裁」而直接报错，用无目标的
     * {@code ON CONFLICT DO NOTHING} 会把 {@code payment_no} 撞号一起吞掉。
     *
     * <p>
     * 供应商付款的 {@code source_id} 为 NULL，落在条件之外，因此 0 行返回值只可能意味着「这张退款已经付过了」，服务层据此给 41139 而不是泄漏约束名。
     * 这也是并发双付款的最终仲裁点：后到者等待前者提交后重新检查条件，得到 0。
     *
     * @return 1 = 本次登记成功（{@code id} 已回填）；0 = 该退款已有付款
     */
    int insertNormalOnConflictDoNothing(FinancePaymentEntity entity);

    /**
     * 锁住原付款行，让反向与核销命令在这行上串行执行。
     */
    FinancePaymentEntity selectByIdForUpdate(@Param("paymentId") Long paymentId);

    /**
     * 按系统来源键取正常付款。
     *
     * <p>
     * 系统登记退款付款时先查这里：重复回调在这一步就返回已有记录，并发下真正的仲裁者仍是 {@code uk_finance_payment_source_active}。
     */
    FinancePaymentEntity selectBySource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    /**
     * 该来源付款的有效核销额（NORMAL 减 REVERSE）。
     */
    BigDecimal selectEffectiveWriteOffAmount(@Param("paymentId") Long paymentId);

    /**
     * 插入反向付款，第二次反向由部分唯一索引仲裁。
     */
    int insertReverseOnConflictDoNothing(FinancePaymentEntity entity);

    BigDecimal selectReversedAmount(@Param("paymentId") Long paymentId);

    FinancePaymentQueryVO selectQueryById(@Param("paymentId") Long paymentId);

    FinancePaymentQueryVO selectReversalByOriginal(@Param("paymentId") Long paymentId);

    List<FinancePaymentQueryVO> queryPage(Page<?> page, @Param("query") FinancePaymentQueryForm query,
        @Param("scope") ScmDataScopeContext scope, @Param("timeRange") ScmDateTimeRange timeRange);
}
