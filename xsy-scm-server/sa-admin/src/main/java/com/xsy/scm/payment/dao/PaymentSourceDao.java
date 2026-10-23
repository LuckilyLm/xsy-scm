package com.xsy.scm.payment.dao;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.payment.domain.dto.PaymentOrderFact;
import com.xsy.scm.payment.domain.dto.PaymentOrderRefundFact;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 支付模块通过只读 DAO 校验订单、退款单与财务付款，不能通过该 DAO 修改其他业务模块的数据。
 */
@Mapper
public interface PaymentSourceDao {
    List<Long> lockOrderRefunds(@Param("orderId") Long orderId);

    List<Long> lockOrderFunding(@Param("orderId") Long orderId);

    boolean hasBalanceRefunds(@Param("orderId") Long orderId);

    boolean hasLegacyRefundFunding(@Param("orderId") Long orderId);

    boolean hasOrderRefundFunding(@Param("orderId") Long orderId);

    boolean customerVisible(@Param("customerId") Long customerId, @Param("scope") ScmValueScope scope);

    /**
     * 销售订单事实（订单号 / 客户 / 业务员 / 结算约定）。
     *
     * <p>
     * 创建支付意图时锁订单，保证状态和结算约定检查与同单并发命令串行；只读详情用 {@link #selectOrder}。
     */
    PaymentOrderFact lockOrder(@Param("orderId") Long orderId);

    PaymentOrderFact selectOrder(@Param("orderId") Long orderId);

    /**
     * 锁定业务退款单。
     *
     * <p>
     * <b>必须加锁</b>：线上退款（本域）与人工退款付款（财务域）要<b>互斥</b>， 两边都锁同一行 {@code xsy_order_refund} 才能把「先查后写」的窗口关掉。
     */
    PaymentOrderRefundFact lockOrderRefund(@Param("refundId") Long refundId);

    PaymentOrderRefundFact selectOrderRefund(@Param("refundId") Long refundId);

    /**
     * 该业务退款单是否已有人工登记的退款付款（财务域 {@code xsy_finance_payment}）。
     *
     * <p>
     * 返回付款单 id；没有则返回 {@code null}。有值时线上退款必须拒绝 —— 否则同一张退款单会被退两次（人工退一次、渠道再退一次）。
     */
    Long selectActiveRefundPayment(@Param("orderRefundId") Long orderRefundId);
}
