package com.xsy.scm.order.support;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.common.exception.ScmBusinessException;

import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_REFERENCED;

/**
 * 客户软删除前检查销售订单引用，复用调用方持有的客户行锁。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class OrderCustomerReferenceGuard {
    private final SalesOrderDao salesOrderDao;

    @Before("execution(* com.xsy.scm.customer.dao.CustomerDao.softDelete(..)) && args(customerId,..)")
    public void beforeCustomerDelete(Long customerId) {
        if (salesOrderDao.customerReferences(customerId) > 0)
            throw new ScmBusinessException(CUSTOMER_REFERENCED);
    }
}
