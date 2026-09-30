package com.xsy.scm.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 应收核销目标的锁定事实与当前订单负责人。 */
@Data
public class FinanceReceivableTargetDto {

    private Long receivableId;

    private String receivableNo;

    private Long orderId;

    private Long customerId;

    private String customerNameSnapshot;

    private String entryType;

    private BigDecimal amount;

    private Long sellerId;
}
