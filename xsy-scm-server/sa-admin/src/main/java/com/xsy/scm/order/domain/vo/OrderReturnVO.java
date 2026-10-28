package com.xsy.scm.order.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

@Data
public class OrderReturnVO {
    private Long returnId;
    private String returnNo;
    private Long orderId;
    /**
     * 独立退货没有原订单，orderNo 为空。
     */
    private String orderNo;
    /**
     * ORDER / INDEPENDENT。
     */
    private String sourceType;
    private Long customerId;
    /**
     * 退货单上的客户名称快照，不随客户主档改名变化。
     */
    private String customerName;
    private String status;
    private String reason;
    private String decisionReason;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal approvedAmount;
    private OffsetDateTime approvedAt;
    private OffsetDateTime rejectedAt;
    private OffsetDateTime cancelledAt;
    private Integer version;
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String updatedBy;
}
