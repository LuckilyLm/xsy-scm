package com.xsy.scm.payment.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/** 逐条对账差异（冻结事实）。 */
@Data
public class PaymentReconciliationItemVO {

    private Long id;

    private Long reconciliationId;

    /** {@code LOCAL_MISSING} / {@code PROVIDER_MISSING} / {@code AMOUNT_MISMATCH} / {@code STATUS_MISMATCH}。 */
    private String category;

    private String providerTransactionNo;

    private Long transactionId;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal localAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal providerAmount;

    private String localStatus;

    private String providerStatus;

    private OffsetDateTime createdAt;
}
