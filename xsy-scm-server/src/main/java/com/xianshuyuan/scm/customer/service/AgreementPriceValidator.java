package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.dto.AgreementPriceSaveRequest;
import org.springframework.stereotype.Component;

@Component
public class AgreementPriceValidator {

    public void validate(AgreementPriceSaveRequest r) {
        if (r.unitPrice().signum() < 0) throw new BusinessException(CustomerErrorCodes.PRICE_INVALID);
        if (r.effectiveTo() != null && !r.effectiveTo().isAfter(r.effectiveFrom()))
            throw new BusinessException(CustomerErrorCodes.PERIOD_INVALID);
    }
}
