package com.xsy.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.time.ScmDateTimeRange;
import com.xsy.scm.finance.domain.entity.FinanceReceiptEntity;
import com.xsy.scm.finance.domain.form.FinanceReceiptQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceReceiptQueryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收款记录只允许新增和查询，错误收款必须登记反向记录。 登记反向记录和核销前需要锁住原收款行，防止并发重复冲回。
 */
@Mapper
public interface FinanceReceiptDao extends BaseMapper<FinanceReceiptEntity> {

    /**
     * 收款单号序列（全局非重置，不按日归零）。必须在事务内调用： {@code nextval} 不随事务回滚，跳号是可接受的代价（与应付单号同一条纪律）。
     */
    long nextReceiptNo();

    /** Lock the source receipt so reversal and write-off commands serialize on the same row. */
    FinanceReceiptEntity selectByIdForUpdate(@Param("receiptId") Long receiptId);

    /**
     * 按系统来源键取正常收款事实（ADM-12）。
     *
     * <p>
     * 系统来源登记收款时先查这里：常见的重复驱动（同一笔支付被多次回调）在这一步就返回已有事实，库上的 {@code uk_finance_receipt_source_active} 才是并发下真正的仲裁者。
     */
    FinanceReceiptEntity selectBySource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    /** Effective write-off amount (NORMAL minus REVERSE) for this source receipt. */
    BigDecimal selectEffectiveWriteOffAmount(@Param("receiptId") Long receiptId);

    /** Insert a reversal, letting the partial unique index arbitrate a second reversal. */
    int insertReverseOnConflictDoNothing(FinanceReceiptEntity entity);

    BigDecimal selectReversedAmount(@Param("receiptId") Long receiptId);

    /**
     * 某张订单的货到付款实收合计，兼容 V131 单笔来源与 V132 分笔来源。
     *
     * <p>
     * 系统来源的收款不允许人工冲正（41144），因此只取 {@code NORMAL} 行即可，不需要再扣 REVERSE。
     */
    BigDecimal sumCodCollectedByOrder(@Param("orderId") Long orderId);

    /** 人工收款经有效核销分配到该订单正常应收的金额。 */
    BigDecimal sumManualCollectedByOrder(@Param("orderId") Long orderId);

    FinanceReceiptQueryVO selectQueryById(@Param("receiptId") Long receiptId);

    FinanceReceiptQueryVO selectReversalByOriginal(@Param("receiptId") Long receiptId);

    List<FinanceReceiptQueryVO> queryPage(Page<?> page, @Param("query") FinanceReceiptQueryForm query,
            @Param("scope") ScmDataScopeContext scope, @Param("timeRange") ScmDateTimeRange timeRange);
}
