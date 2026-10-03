package com.xsy.scm.finance.support;

import java.math.BigDecimal;

/** 自动分配的逐来源结果；跳过原因不会被解释为整单结清。 */
public record FinanceFundingAllocationResult(Long transactionId, Status status, BigDecimal appliedAmount) {
    public enum Status {
        APPLIED, ALREADY_ALLOCATED, REVERSED, NO_AVAILABLE_FUNDS, PARTIALLY_AVAILABLE, NO_RECEIVABLE
    }
}
