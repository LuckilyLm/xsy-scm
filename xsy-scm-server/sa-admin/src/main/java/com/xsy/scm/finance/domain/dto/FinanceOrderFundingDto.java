package com.xsy.scm.finance.domain.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/** 财务只读的订单资金来源及其身份。 */
@Data
public class FinanceOrderFundingDto {
    private Long intentId;
    private Long orderId;
    private Long customerId;
    private Long settlementCustomerId;
    private Long transactionId;
    private Long receiptId;
    private Long receiptCustomerId;
    private Long receiptSettlementCustomerId;
    private BigDecimal receiptAmount;
    private BigDecimal transactionAmount;
    private OffsetDateTime movementOccurredAt;
    private Long movementId;
    private Long movementCustomerId;
    private Long movementSettlementCustomerId;
    private Long movementIntentId;
    private String operator;
    private String method;
    private String provider;
    private String intentStatus;
    private String transactionStatus;
    private String transactionProvider;
    private String sourceType;
    private String settlementCustomerName;
    private String movementType;
    private String movementDirection;
    private String movementSourceType;
    private BigDecimal amount;
    private BigDecimal providerAmount;
    private BigDecimal movementAmount;
    private OffsetDateTime paidAt;
}
