package com.xsy.scm.finance.domain.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class FinancePurchaseCostSourceDto {
    private Long id;
    private String adjustmentNo;
    private Long accountItemId;
    private Long accountId;
    private String accountNo;
    private Long receiptItemId;
    private Long receiptId;
    private Long purchaseOrderId;
    private Long purchaseOrderItemId;
    private Long supplierId;
    private String supplierNameSnapshot;
    private Long skuId;
    private String productNameSnapshot;
    private String direction;
    private BigDecimal amount;
    private String reason;
    private OffsetDateTime occurredAt;
    private String createdBy;
}
