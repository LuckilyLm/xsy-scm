package com.xsy.scm.finance.service;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.dao.CustomerCreditDao;
import com.xsy.scm.finance.domain.dto.FinanceCreditExposureDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Read-only account facts; customer locks and order decisions belong to the caller. */
@Service
@RequiredArgsConstructor
public class FinanceCreditExposureQueryService {
    private final CustomerCreditDao customerCreditDao;

    public FinanceCreditExposureDto summarize(Long settlementCustomerId, ScmValueScope orderSellerScope) {
        return customerCreditDao.selectExposure(settlementCustomerId, orderSellerScope);
    }
}
