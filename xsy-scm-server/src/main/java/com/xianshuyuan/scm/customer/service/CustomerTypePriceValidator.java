package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.dto.CustomerTypePriceSaveRequest;
import org.springframework.stereotype.Component;

@Component
public class CustomerTypePriceValidator {
    public void validate(CustomerTypePriceSaveRequest request) {
        if (request.unitPrice().signum() < 0) {
            throw new BusinessException(CustomerErrorCodes.PRICE_INVALID);
        }
        if (request.effectiveTo() != null && !request.effectiveTo().isAfter(request.effectiveFrom())) {
            throw new BusinessException(CustomerErrorCodes.PERIOD_INVALID);
        }
    }
}
