package com.xsy.scm.payment.domain.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 发起退款。
 *
 * <p>
 * <b>资金来源是 {@code PaymentTransaction}，不是售后退款单</b>：退款必须退到某一笔<b>实际收到的</b> 渠道交易上。{@code orderRefundId}
 * 只是业务来源标记（用于「一张售后退款单只映射一笔渠道退款」）， 不参与金额判定。
 */
@Data
public class PaymentRefundCreateForm {

    @NotNull(message = "原支付交易不能为空")
    @Positive(message = "支付交易 ID 必须大于0")
    private Long transactionId;

    @NotNull(message = "退款金额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "退款金额必须大于0")
    @Digits(integer = 14, fraction = 4, message = "退款金额最多14位整数和4位小数")
    private BigDecimal amount;

    /** 业务来源，当前只支持 {@code ORDER_REFUND}。 */
    private String sourceType;

    /** 业务来源 id（售后退款单 id）。与 {@code sourceType} 要么都空、要么都齐。 */
    private Long sourceId;

    private String reason;

    /** 仅本地模拟渠道可指定：{@code SUCCESS} / {@code FAILURE}。 */
    private String mockScenario;
}
