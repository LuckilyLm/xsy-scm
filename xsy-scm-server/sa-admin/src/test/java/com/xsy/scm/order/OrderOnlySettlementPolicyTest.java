package com.xsy.scm.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.customer.service.CustomerTypeService;
import com.xsy.scm.finance.service.FinanceOrderPayableQueryService;
import com.xsy.scm.order.constant.OrderErrorCode;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.service.OrderCreditService;
import com.xsy.scm.order.service.OrderSettlementConfirmationPolicy;
import org.junit.jupiter.api.Test;

class OrderOnlySettlementPolicyTest {
    private final OrderSettlementConfirmationPolicy policy = new OrderSettlementConfirmationPolicy(
        mock(CustomerService.class), mock(CustomerTypeService.class), mock(OrderCreditService.class),
        mock(FinanceOrderPayableQueryService.class));

    @Test
    void newOrderRequiresExplicitSupportedSettlement() {
        SalesOrderEntity order = new SalesOrderEntity();
        order.setMallOrderOnly(true);
        assertThatThrownBy(() -> policy.prepare(order, null))
            .isInstanceOfSatisfying(ScmBusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_SETTLEMENT_METHOD_REQUIRED));
        assertThatThrownBy(() -> policy.prepare(order, "UNKNOWN")).isInstanceOf(ScmBusinessException.class);
        assertThat(order.getSettlementMethod()).isNull();
        policy.prepare(order, "COD");
        assertThat(order.getSettlementMethod()).isEqualTo("COD");
    }

    @Test
    void existingSettlementCannotBeChanged() {
        SalesOrderEntity order = new SalesOrderEntity();
        order.setMallOrderOnly(true);
        order.setSettlementMethod("WALLET");
        assertThatThrownBy(() -> policy.prepare(order, "COD")).isInstanceOf(ScmBusinessException.class);
        assertThat(order.getSettlementMethod()).isEqualTo("WALLET");
        policy.prepare(order, null);
        policy.prepare(order, "WALLET");
        assertThat(order.getSettlementMethod()).isEqualTo("WALLET");
    }

    @Test
    void legacyNullMethodKeepsOriginalConfirmationPath() {
        SalesOrderEntity order = new SalesOrderEntity();
        policy.prepare(order, null);
        assertThat(order.getSettlementMethod()).isNull();
        assertThatThrownBy(() -> policy.prepare(order, "COD")).isInstanceOf(ScmBusinessException.class);
    }
}
