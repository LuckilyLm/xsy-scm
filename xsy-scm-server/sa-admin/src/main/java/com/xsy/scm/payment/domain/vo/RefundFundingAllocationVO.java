package com.xsy.scm.payment.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class RefundFundingAllocationVO {
    private Long allocationId;
    private String sourceMethod;
    private Long sourceId;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal sourcePrincipal;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal allocatedAmount;
    private String status;
    private Long resultSourceId;
    private String failureCode;
    private String failureMessage;
    private OffsetDateTime sourceSucceededAt;
}
