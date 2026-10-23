package com.xsy.scm.purchase.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class PurchaseShareFeedbackVO {
    private Long id;
    private Long purchaseOrderItemId;
    private String productName;
    private String skuName;
    private String purchaseUnit;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal unitPrice;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal receivedQuantity;
    private String remark;
    private OffsetDateTime submittedAt;
}
