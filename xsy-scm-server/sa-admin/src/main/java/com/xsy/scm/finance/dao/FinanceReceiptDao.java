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
 * 收款读写。
 *
 * <p>
 * <b>登错只能反向，不能改</b>：本接口刻意不声明 update / delete， {@code ck_finance_receipt_append_only} 在库层拒绝软删，
 * {@code uk_finance_receipt_single_reverse} 保证一条 {@code NORMAL} 最多被反向一次。
 *
 * <p>
 * 的登记与 的反向都必须先 {@code SELECT … FOR UPDATE} 锁住原行 （{@code FinanceConstant.LOCK_RANK_RECEIPT}），再校验已用额；反向与核销共用这把锁，
 * 因此「反向前已用额 = 0」这条前置在并发下才成立。
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
     * 按系统来源键取正常收款事实（ADM-12 3-11a）。
     *
     * <p>
     * 系统来源登记收款时先查这里：常见的重复驱动（同一笔支付被多次回调）在这一步就返回已有事实，
     * 库上的 {@code uk_finance_receipt_source_active} 才是并发下真正的仲裁者。
     */
    FinanceReceiptEntity selectBySource(@Param("sourceType") String sourceType,
            @Param("sourceId") Long sourceId);

    /** Effective write-off amount (NORMAL minus REVERSE) for this source receipt. */
    BigDecimal selectEffectiveWriteOffAmount(@Param("receiptId") Long receiptId);

    /** Insert a reversal, letting the partial unique index arbitrate a second reversal. */
    int insertReverseOnConflictDoNothing(FinanceReceiptEntity entity);

    BigDecimal selectReversedAmount(@Param("receiptId") Long receiptId);

    FinanceReceiptQueryVO selectQueryById(@Param("receiptId") Long receiptId);

    FinanceReceiptQueryVO selectReversalByOriginal(@Param("receiptId") Long receiptId);

    List<FinanceReceiptQueryVO> queryPage(Page<?> page, @Param("query") FinanceReceiptQueryForm query,
            @Param("scope") ScmDataScopeContext scope, @Param("timeRange") ScmDateTimeRange timeRange);
}
