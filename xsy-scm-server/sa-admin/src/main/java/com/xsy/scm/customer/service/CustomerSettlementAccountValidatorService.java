package com.xsy.scm.customer.service;

import com.xsy.scm.common.contract.CustomerSettlementAccountValidator;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exposes customer-owned settlement validation to consumers through a read-only contract. */
@Service
@RequiredArgsConstructor
public class CustomerSettlementAccountValidatorService implements CustomerSettlementAccountValidator {

    private final CustomerService customerService;

    @Override
    @Transactional(readOnly = true)
    public void validateSettlementRelationship(Long customerId) {
        CustomerEntity customer = customerService.require(customerId);
        customerService.requireSettlementAccount(customer);
    }
}
