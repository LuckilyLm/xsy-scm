package com.xsy.scm.balance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/** 余额流水（对外只读视图）。 */
@Data
public class BalanceMovementVO {

    private Long id;

    private String movementNo;

    private Long accountId;

    private Long settlementCustomerId;

    private Long customerId;

    private String type;

    private String direction;

    /** 金额恒正；正负由 {@link #direction} 表达。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal amount;

    private String sourceType;

    private Long sourceId;

    private String reason;

    private OffsetDateTime occurredAt;

    private OffsetDateTime createdAt;

    private String createdBy;
}
