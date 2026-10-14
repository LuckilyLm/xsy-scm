package com.xsy.scm.finance;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.service.FinanceCreditExposureQueryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Customer credit exposure seller-scope SQL (PG IT)")
class CustomerCreditScopePgIT extends ScmW5PgITBase {

    @Autowired
    private FinanceCreditExposureQueryService exposureQueryService;

    @Test
    @DisplayName("非空 seller 范围绑定 foreach、按订单归属判定可见性；占用金额由服务层按净额补齐")
    void nonEmptySellerScopeBindsIdsAndFiltersConfirmedOrders() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku(prefix);
        confirmedSalesOrder(customerId, skuId, "2.0000", "2.0000");
        Long sellerId = customerService.require(customerId).getSellerId();

        // 范围不含该订单的负责人：可见性为 false，调用方据此失败关闭，不会拿残缺敞口做授信裁定
        var outsideScope = exposureQueryService.summarize(customerId, ScmValueScope.of(List.of(Long.MAX_VALUE)));
        assertThat(outsideScope.getExposureVisible()).isFalse();
        assertThat(outsideScope.getConfirmedOrderAmount())
                .as("占用金额由服务层按正式净额补齐，不再由这条 SQL 汇总")
                .isGreaterThan(BigDecimal.ZERO);

        var owningScope = exposureQueryService.summarize(customerId, ScmValueScope.of(List.of(sellerId)));
        assertThat(owningScope.getExposureVisible()).isTrue();
        assertThat(owningScope.getConfirmedOrderAmount()).isGreaterThan(BigDecimal.ZERO);
    }
}
