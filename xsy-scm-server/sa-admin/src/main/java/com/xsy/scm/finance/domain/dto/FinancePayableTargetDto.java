package com.xsy.scm.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 应付核销目标的锁定事实与当前采购负责人。 */
@Data
public class FinancePayableTargetDto {

    private Long payableId;

    private String payableNo;

    private Long purchaseOrderId;

    private Long supplierId;

    private String supplierNameSnapshot;

    private String entryType;

    private BigDecimal amount;

    private Long purchaserId;
}
