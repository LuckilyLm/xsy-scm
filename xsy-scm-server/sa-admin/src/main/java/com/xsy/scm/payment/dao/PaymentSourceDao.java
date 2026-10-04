package com.xsy.scm.payment.dao;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.payment.domain.dto.PaymentOrderFact;
import com.xsy.scm.payment.domain.dto.PaymentOrderRefundFact;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 支付域读取的**外部只读事实**（订单 / 退款单 / 财务付款）。
 *
 * <p>
 * 形态照财务域的 {@code FinancePaymentSourceDao}：**不继承 BaseMapper** —— 支付域对这三张表
 * 没有写入口，只读它们来校验来源身份。刻意不走别的域的 DAO 接口：那会让「谁可以读什么」
 * 变成跨域对象图的一部分，而这里真正需要的只是三行 SQL。
 */
@Mapper
public interface PaymentSourceDao {
    List<Long> lockOrderRefunds(@Param("orderId") Long orderId);
    boolean hasBalanceRefunds(@Param("orderId") Long orderId);

    boolean hasOrderRefundFunding(@Param("orderId") Long orderId);
    boolean customerVisible(@Param("customerId") Long customerId,
            @Param("scope") ScmValueScope scope);

    /**
     * 销售订单事实（订单号 / 客户 / 业务员）。
     *
     * <p>
     * 不加锁：订单的客户与单号在创建后不会变，意图只需要冻结它们的当前值。
     * 真正的并发争用点在退款路径（见 {@link #lockOrderRefund}）。
     */
    PaymentOrderFact lockOrder(@Param("orderId") Long orderId);

    PaymentOrderFact selectOrder(@Param("orderId") Long orderId);

    /**
     * 锁定业务退款单。
     *
     * <p>
     * <b>必须加锁</b>：线上退款（本域）与人工退款付款（财务域）要**互斥**，
     * 两边都锁同一行 {@code order_refund} 才能把「先查后写」的窗口关掉。
     */
    PaymentOrderRefundFact lockOrderRefund(@Param("refundId") Long refundId);

    /**
     * 该业务退款单是否已有人工登记的退款付款（财务域 {@code finance_payment}）。
     *
     * <p>
     * 返回付款单 id；没有则返回 {@code null}。有值时线上退款必须拒绝 ——
     * 否则同一张退款单会被退两次（人工退一次、渠道再退一次）。
     */
    Long selectActiveRefundPayment(@Param("orderRefundId") Long orderRefundId);
}
