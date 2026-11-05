package com.xsy.scm.common.contract;

/**
 * 客户域的只读接口，用来校验结算关系配置。
 */
public interface CustomerSettlementAccountValidator {

    void validateSettlementRelationship(Long customerId);
}
