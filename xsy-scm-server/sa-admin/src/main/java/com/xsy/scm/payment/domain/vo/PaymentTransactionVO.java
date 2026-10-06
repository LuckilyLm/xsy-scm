package com.xsy.scm.payment.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/** 交易事实。 */
@Data
public class PaymentTransactionVO {

    private Long id;

    private String transactionNo;

    private Long intentId;

    private String provider;

    private String providerTransactionNo;

    /** 本地应付金额。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal amount;

    /**
     * 渠道回报的实收金额。
     *
     * <p>
     * 与 {@link #amount} 分开：它是<b>退款可退本金</b>的依据，也是对账 {@code AMOUNT_MISMATCH} 的比对源。 为空表示渠道还没回报，此时不允许退款。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal providerAmount;

    private String status;

    private OffsetDateTime paidAt;

    private String failureCode;

    private String failureMessage;

    private OffsetDateTime createdAt;

    private String createdBy;
}
