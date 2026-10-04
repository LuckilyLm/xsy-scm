package com.xsy.scm.finance;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.dao.CustomerCreditDao;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Customer credit exposure seller-scope SQL (PG IT)")
class CustomerCreditScopePgIT extends ScmW5PgITBase {

    @Autowired
    private CustomerCreditDao customerCreditDao;

    @Test
    @DisplayName("非空 seller 范围绑定 foreach 并按订单归属判定 exposure 可见性")
    void nonEmptySellerScopeBindsIdsAndFiltersConfirmedOrders() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku(prefix);
        confirmedSalesOrder(customerId, skuId, "2.0000", "2.0000");
        Long sellerId = customerService.require(customerId).getSellerId();

        var outsideScope = customerCreditDao.selectExposure(customerId, ScmValueScope.of(List.of(Long.MAX_VALUE)));
        assertThat(outsideScope.getConfirmedOrderAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(outsideScope.getExposureVisible()).isFalse();

        var owningScope = customerCreditDao.selectExposure(customerId, ScmValueScope.of(List.of(sellerId)));
        assertThat(owningScope.getConfirmedOrderAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(owningScope.getExposureVisible()).isTrue();
    }
}
