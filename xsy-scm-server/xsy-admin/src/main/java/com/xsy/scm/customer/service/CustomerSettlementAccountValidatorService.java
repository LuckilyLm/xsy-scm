package com.xsy.scm.customer.service;

import com.xsy.scm.common.contract.CustomerSettlementAccountValidator;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通过只读接口向其他域提供客户结算主体校验。
 */
@Service
@RequiredArgsConstructor
public class CustomerSettlementAccountValidatorService implements CustomerSettlementAccountValidator {

    private final CustomerService customerService;

    @Override
    @Transactional(readOnly = true)
    public void validateSettlementRelationship(Long customerId) {
        try {
            CustomerEntity customer = customerService.require(customerId);
            customerService.requireSettlementAccount(customer);
        } catch (ScmBusinessException exception) {
            // 结算主体配置有问题时，财务收款入参即无效；引用的客户被删除或从未存在都按同一错误处理。
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
    }
}
