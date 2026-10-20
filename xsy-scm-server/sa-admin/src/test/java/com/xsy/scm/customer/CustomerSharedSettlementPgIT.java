package com.xsy.scm.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.vo.CustomerDetailVO;
import com.xsy.scm.customer.service.CustomerQueryService;
import com.xsy.scm.customer.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 集团共享结算授权：默认关闭、只在集团主体上成立。
 *
 * <p>
 * 授权决定子客户能不能花集团的钱，所以两层都要钉住：服务层按结算模式收口，数据库再挡一次，
 * 免得有人绕过服务直接改表把独立结算的客户变成集团授权方。
 */
class CustomerSharedSettlementPgIT extends ScmW5PgITBase {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerQueryService customerQueryService;

    @Test
    @DisplayName("#1 集团主体可授权，独立结算客户即使提交也会被置假")
    void onlyGroupCustomerKeepsGrant() {
        Long groupId = customerService.add(form("W9 集团", "GROUP", true));
        CustomerDetailVO group = customerQueryService.detail(groupId);
        assertThat(group.getSharedSettlementEnabled()).isTrue();

        Long independentId = customerService.add(form("W9 独立", "INDEPENDENT", true));
        CustomerDetailVO independent = customerQueryService.detail(independentId);
        assertThat(independent.getSharedSettlementEnabled()).isFalse();
    }

    @Test
    @DisplayName("#2 数据库拒绝给非集团客户打开授权")
    void databaseRejectsGrantOnIndependentCustomer() {
        Long customerId = customerService.add(form("W9 约束", "INDEPENDENT", false));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE xsy_customer SET shared_settlement_enabled = TRUE WHERE id = ?", customerId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private CustomerAddForm form(String name, String settleMode, boolean shared) {
        CustomerAddForm form = new CustomerAddForm();
        form.setName(name);
        form.setCustomerTypeId(customerTypeId("ENTERPRISE"));
        form.setSettleMode(settleMode);
        form.setSharedSettlementEnabled(shared);
        return form;
    }
}
