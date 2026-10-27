package com.xsy.scm.finance.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import lombok.Data;

/**
 * 抹零记录：金额、原因、状态，以及由它产生的那条核销行。
 */
@Data
public class FinanceWaiverVO {

    private Long waiverId;

    private String waiverNo;

    private Long receivableId;

    private String receivableNo;

    private Long customerId;

    private String customerName;

    private Long settlementCustomerId;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal amount;

    private String reason;

    /**
     * {@code ScmFinanceWaiverStatusEnum}：{@code NORMAL / REVERSED}。
     */
    private String status;

    private OffsetDateTime reversedAt;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String reversedBy;

    private String reverseReason;

    /** 冲减余额的那条核销行；对账时按核销口径解释，不另算一套余额。 */
    private Long writeOffId;

    private String writeOffNo;

    private OffsetDateTime createdAt;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;
}
