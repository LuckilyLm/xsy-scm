package com.xsy.scm.finance.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.finance.domain.dto.FinanceRefundFactDto;
import com.xsy.scm.finance.domain.form.FinanceRefundOptionQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceRefundOptionVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 付款登记读取的**来源事实**（只读跨域 DAO，形态照 {@link FinancePayableSourceDao}）。
 *
 * <p>
 * 刻意不继承 {@code BaseMapper}：财务域对业务表没有写入口； 对方主档（客户 / 供应商）走 {@link FinanceCounterpartySourceDao}，本 DAO 只管来源单据。
 */
@Mapper
public interface FinancePaymentSourceDao {

    /**
     * 未删除的退款行（状态 + 客户 + 应退金额），供 的三条校验使用。
     *
     * <p>
     * 读侧不带 {@code status} 过滤：{@code PENDING} 必须能读出来，服务层才能给出 可解释的 41139，而不是把它伪装成「退款不存在」。
     */
    FinanceRefundFactDto lockOrderRefund(@Param("refundId") Long refundId);

    FinanceRefundFactDto selectOrderRefund(@Param("refundId") Long refundId);

    /**
     * 该业务退款单是否已有**线上退款事实**（支付域 {@code payment_refund}）。
     *
     * <p>
     * 人工退款付款与线上退款必须互斥：同一张退款单被退两次（人工一次、渠道一次）
     * 是最难查的一类账。两边各自锁同一行 {@code order_refund}，再各自查对方有没有事实，
     * 窗口就关上了。返回退款单 id；没有则返回 {@code null}。
     */
    Long selectActivePaymentRefund(@Param("orderRefundId") Long orderRefundId);

    /** Paginated completed refund picker; excludes already-paid sources and follows customer seller scope. */
    List<FinanceRefundOptionVO> selectCompletedRefundOptions(Page<?> page,
            @Param("query") FinanceRefundOptionQueryForm query, @Param("scope") ScmDataScopeContext scope);
}
