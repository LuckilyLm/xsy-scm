package com.xsy.scm.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class OrderRefundVO {
    private Long refundId;
    private Long balanceMovementId;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal balanceReturnedAmount;
    private String refundNo;
    private Long returnId;
    private Long orderId;
    private Long customerId;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal refundAmount;
    private String status;
    private String externalReference;
    private OffsetDateTime completedAt;
    private Integer version;
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String updatedBy;
}
