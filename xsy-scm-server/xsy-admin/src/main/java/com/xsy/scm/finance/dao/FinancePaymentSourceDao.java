package com.xsy.scm.finance.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.finance.domain.dto.FinanceRefundFactDto;
import com.xsy.scm.finance.domain.form.FinanceRefundOptionQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceRefundOptionVO;

import java.util.List;
import java.math.BigDecimal;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 付款登记读取的来源单据（只读跨域 DAO，形态同 {@link FinancePayableSourceDao}）。
 *
 * <p>
 * 刻意不继承 {@code BaseMapper}：财务域对业务表没有写入口；对方主档（客户 / 供应商）走 {@link FinanceCounterpartySourceDao}，本 DAO 只管来源单据。
 */
@Mapper
public interface FinancePaymentSourceDao {

    /**
     * 未删除的退款行（状态 + 客户 + 应退金额），供付款侧的三条校验使用。
     *
     * <p>
     * 读侧不带 {@code status} 过滤：{@code PENDING} 必须能读出来，服务层才能给出可解释的 41139，而不是把它伪装成「退款不存在」。
     */
    FinanceRefundFactDto lockOrderRefund(@Param("refundId") Long refundId);

    FinanceRefundFactDto selectOrderRefund(@Param("refundId") Long refundId);

    boolean hasRefundFundingAllocations(@Param("orderId") Long orderId);

    boolean matchesSuccessfulRefund(@Param("refundId") Long refundId, @Param("paymentRefundId") Long paymentRefundId,
        @Param("customerId") Long customerId, @Param("amount") BigDecimal amount);

    /**
     * 人工退款付款和渠道退款不能同时支付同一退款单。两者都锁原退款单并检查另一条支付路径是否已执行。
     */
    Long selectActivePaymentRefund(@Param("orderRefundId") Long orderRefundId);

    /**
     * 分页的已完成退款选择器；排除已付款来源，并跟随客户业务员范围。
     */
    List<FinanceRefundOptionVO> selectCompletedRefundOptions(Page<?> page,
        @Param("query") FinanceRefundOptionQueryForm query, @Param("scope") ScmDataScopeContext scope);
}
