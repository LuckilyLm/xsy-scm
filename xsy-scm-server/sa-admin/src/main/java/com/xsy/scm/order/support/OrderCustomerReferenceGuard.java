package com.xsy.scm.order.support;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.common.exception.ScmBusinessException;

import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_REFERENCED;

/**
 * Checks sales-order references before customer soft deletion, using the caller's customer-row lock.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class OrderCustomerReferenceGuard {
    private final SalesOrderDao salesOrderDao;

    @Before("execution(* com.xsy.scm.customer.dao.CustomerDao.softDelete(..)) && args(customerId,..)")
    public void beforeCustomerDelete(Long customerId) {
        if (salesOrderDao.customerReferences(customerId) > 0) throw new ScmBusinessException(CUSTOMER_REFERENCED);
    }
}
