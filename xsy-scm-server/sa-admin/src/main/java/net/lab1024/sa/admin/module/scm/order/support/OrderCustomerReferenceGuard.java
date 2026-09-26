package net.lab1024.sa.admin.module.scm.order.support;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import net.lab1024.sa.admin.module.scm.order.dao.SalesOrderDao;
import com.xsy.scm.common.exception.ScmBusinessException;

import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_REFERENCED;

/**
 * Order-owned reference adapter. W2 source remains frozen; its transaction and customer lock are reused.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class OrderCustomerReferenceGuard {
    private final SalesOrderDao orders;

    @Before("execution(* com.xsy.scm.customer.dao.CustomerDao.softDelete(..)) && args(id,..)")
    public void beforeCustomerDelete(Long id) {
        if (orders.customerReferences(id) > 0) throw new ScmBusinessException(CUSTOMER_REFERENCED);
    }
}
