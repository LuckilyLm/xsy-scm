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

    /** 钱包所有者（结算主体）。 */
    private Long settlementCustomerId;

    private String settlementCustomerName;

    /** 本次业务实际发生的客户；集团场景下与钱包所有者不同。 */
    private Long customerId;

    private String customerName;

    /** {@code RECHARGE} / {@code CONSUME} / {@code REFUND} / {@code CORRECTION}。 */
    private String movementType;

    private String direction;

    /** 金额恒正；正负由 {@link #direction} 表达。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal amount;

    private String sourceType;

    private Long sourceId;

    private String reason;

    private OffsetDateTime occurredAt;

    private OffsetDateTime createdAt;

    /** 操作人（写入流水的人；系统入账时为服务账号）。 */
    private String operator;
}
