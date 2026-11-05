package com.xsy.scm.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class OrderReceivedCorrectionCandidateVO {
    private Long orderItemId;
    private Long lastCorrectionId;
    private String saleUnit;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal shippedQuantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal currentReceivedQuantity;
}
