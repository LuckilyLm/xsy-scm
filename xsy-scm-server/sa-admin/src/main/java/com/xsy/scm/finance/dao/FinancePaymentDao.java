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
 * 付款读写，append-only 契约同 {@link FinanceReceiptDao}。
 *
 * <p>
 * 退款付款的行级防重锚点是 {@code uk_finance_payment_source_active}
 * （{@code (source_type, source_id) WHERE deleted = FALSE AND source_id IS NOT NULL}）：同一张 {@code order_refund}
 * 最多一笔正式退款付款。反向付款的 {@code source_id} 必须为 NULL，因此不与原行抢这个键。
 */
@Mapper
public interface FinancePaymentDao extends BaseMapper<FinancePaymentEntity> {

    /**
     * 付款单号序列（全局非重置，不按日归零）。必须在事务内调用： {@code nextval} 不随事务回滚，跳号是可接受的代价（与应付 / 收款单号同一条纪律）。
     */
    long nextPaymentNo();

    /**
     * 插入一笔正常付款，同一 {@code ORDER_REFUND} 已有付款时什么都不做。
     *
     * <p>
     * 冲突目标与 {@code uk_finance_payment_source_active} 的列和谓词<b>逐字一致</b>（纪律）：少写谓词会命中「无索引可仲裁」而直接报错，用无目标的
     * {@code ON CONFLICT DO NOTHING} 会把 {@code payment_no} 撞号一起吞掉。
     *
     * <p>
     * 供应商付款的 {@code source_id} 为 NULL，落在谓词之外，因此本方法的 0 行返回值 <b>只可能</b>意味着「这张退款已经付过了」，服务层据此给 41139 而不是泄漏约束名。
     * 这也是并发双付款的最终仲裁点：后到者在此等待前者提交后重新检查谓词，得到 0。
     *
     * @return 1 = 本次登记成功（{@code id} 已回填）；0 = 该退款已有付款
     */
    int insertNormalOnConflictDoNothing(FinancePaymentEntity entity);

    /** Lock the source payment so reversal and write-off commands serialize on the same row. */
    FinancePaymentEntity selectByIdForUpdate(@Param("paymentId") Long paymentId);

    /**
     * 按系统来源键取正常付款事实（ADM-12）。
     *
     * <p>
     * 系统登记退款付款时先查这里：重复驱动（同一笔退款被多次回调）在这一步就返回已有事实， 库上的 {@code uk_finance_payment_source_active} 是并发下真正的仲裁者。
     */
    FinancePaymentEntity selectBySource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    /** Effective write-off amount (NORMAL minus REVERSE) for this source payment. */
    BigDecimal selectEffectiveWriteOffAmount(@Param("paymentId") Long paymentId);

    /** Insert a reversal, letting the partial unique index arbitrate a second reversal. */
    int insertReverseOnConflictDoNothing(FinancePaymentEntity entity);

    BigDecimal selectReversedAmount(@Param("paymentId") Long paymentId);

    FinancePaymentQueryVO selectQueryById(@Param("paymentId") Long paymentId);

    FinancePaymentQueryVO selectReversalByOriginal(@Param("paymentId") Long paymentId);

    List<FinancePaymentQueryVO> queryPage(Page<?> page, @Param("query") FinancePaymentQueryForm query,
            @Param("scope") ScmDataScopeContext scope, @Param("timeRange") ScmDateTimeRange timeRange);
}
