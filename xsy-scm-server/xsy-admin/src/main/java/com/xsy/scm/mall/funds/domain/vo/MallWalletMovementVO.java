package com.xsy.scm.mall.funds.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class MallWalletMovementVO {
    private String movementNo;
    private String movementType;
    private String direction;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal amount;

    private OffsetDateTime occurredAt;
    private String orderNo;
    private String refundNo;
    private String rechargeNo;
    private Long returnId;
}
