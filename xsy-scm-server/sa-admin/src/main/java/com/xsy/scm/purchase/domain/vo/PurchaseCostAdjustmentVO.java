package com.xsy.scm.purchase.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import lombok.Data;

@Data
public class PurchaseCostAdjustmentVO {
    private Long id;
    private String adjustmentNo;
    private Long accountItemId;
    private Long accountId;
    private String accountNo;
    private Long receiptItemId;
    private Long receiptId;
    private String receiptNo;
    private Long purchaseOrderId;
    private Long purchaseOrderItemId;
    private Long warehouseId;
    private Long supplierId;
    private String supplierNameSnapshot;
    private Long skuId;
    private String productNameSnapshot;
    private String unitSnapshot;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;
    private Long previousAdjustmentId;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal previousPrice;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal newPrice;
    private String direction;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal amount;
    private String reason;
    private OffsetDateTime occurredAt;
    private String createdBy;
    @JsonIgnore
    private Long purchaserId;
}
