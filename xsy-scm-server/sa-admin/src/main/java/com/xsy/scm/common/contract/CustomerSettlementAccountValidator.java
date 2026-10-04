package com.xsy.scm.common.contract;

/** Read-only customer-domain contract for validating a configured settlement relationship. */
public interface CustomerSettlementAccountValidator {

    void validateSettlementRelationship(Long customerId);
}
