package com.xsy.scm.customer;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.balance.domain.form.BalanceCorrectionForm;
import com.xsy.scm.balance.service.CustomerBalanceService;
import com.xsy.scm.common.ScmW2PgITBase;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerDeleteForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.customer.domain.form.CustomerUpdateForm;
import com.xsy.scm.customer.service.CustomerQueryService;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.payment.domain.form.PaymentRefundQueryForm;
import com.xsy.scm.payment.service.PaymentQueryService;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

class ScmCustomerBalanceScopePgIT extends ScmW2PgITBase {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerQueryService customerQueryService;

    @Autowired
    private CustomerBalanceService customerBalanceService;

    @Autowired
    private PaymentQueryService paymentQueryService;

    private Long sellerA;
    private Long sellerB;

    @BeforeEach
    void employees() {
        sellerA = newEmployee("A");
        sellerB = newEmployee("B");
    }

    @Test
    void customerWritesAndOptionsUseTheSameOwnerScope() {
        Long customerId = as(sellerB, () -> customerService.add(customerForm()));
        Integer version = jdbc.queryForObject("SELECT version FROM customer WHERE id = ?", Integer.class, customerId);

        assertThat(as(sellerA, () -> customerQueryService.optionList())).noneMatch(option -> customerId.equals(option.getCustomerId()));
        assertThat(as(sellerB, () -> customerQueryService.optionList())).anyMatch(option -> customerId.equals(option.getCustomerId()));
        assertThatThrownBy(() -> as(sellerA, () -> {
            customerService.update(updateForm(customerId, version));
            return null;
        }))
                .isInstanceOf(ScmDataScopeException.class);

        CustomerStatusForm status = new CustomerStatusForm();
        status.setCustomerId(customerId);
        status.setVersion(version);
        status.setStatus("COOPERATING");
        assertThatThrownBy(() -> as(sellerA, () -> {
            customerService.updateStatus(status);
            return null;
        }))
                .isInstanceOf(ScmDataScopeException.class);

        CustomerDeleteForm deletion = new CustomerDeleteForm();
        deletion.setCustomerId(customerId);
        deletion.setVersion(version);
        assertThatThrownBy(() -> as(sellerA, () -> {
            customerService.delete(deletion);
            return null;
        }))
                .isInstanceOf(ScmDataScopeException.class);
        assertThat(jdbc.queryForObject("SELECT version FROM customer WHERE id = ?", Integer.class, customerId))
                .isEqualTo(version);
    }

    @Test
    void balanceCorrectionReplayRechecksCurrentOwner() {
        Long customerId = as(sellerB, () -> customerService.add(customerForm()));
        BalanceCorrectionForm form = new BalanceCorrectionForm();
        form.setCustomerId(customerId);
        form.setDirection("CREDIT");
        form.setAmount(new BigDecimal("10.0000"));
        form.setReason("范围回归");
        String key = prefix + "-correction";

        assertThatThrownBy(() -> as(sellerA, () -> customerBalanceService.correct(form, key)))
                .isInstanceOf(ScmDataScopeException.class);
        as(sellerB, () -> customerBalanceService.correct(form, key));
        jdbc.update("UPDATE customer SET seller_id = ? WHERE id = ?", sellerA, customerId);
        assertThatThrownBy(() -> as(sellerB, () -> customerBalanceService.correct(form, key)))
                .isInstanceOf(ScmDataScopeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_balance_movement WHERE customer_id = ?",
                Integer.class, customerId)).isEqualTo(1);
    }

    @Test
    void refundListIntersectsCustomerAndOrderScope() {
        Long customerA = as(sellerA, () -> customerService.add(customerForm()));
        Long customerB = as(sellerB, () -> customerService.add(customerForm()));
        Long orderA = order(customerA, sellerA, "A");
        Long orderB = order(customerB, sellerB, "B");
        Long refundA = refund(customerA, orderA, "A");
        Long refundB = refund(customerB, orderB, "B");
        Long mismatched = refund(customerA, orderB, "M");

        PaymentRefundQueryForm form = new PaymentRefundQueryForm();
        form.setPageNum(1L);
        form.setPageSize(20L);
        assertThat(as(sellerA, () -> paymentQueryService.refundPage(form)).getList())
                .extracting(row -> row.getId()).contains(refundA).doesNotContain(refundB, mismatched);
        assertThat(as(sellerB, () -> paymentQueryService.refundPage(form)).getList())
                .extracting(row -> row.getId()).contains(refundB).doesNotContain(refundA, mismatched);
    }

    private Long order(Long customerId, Long sellerId, String suffix) {
        String customerCode = jdbc.queryForObject("SELECT customer_code FROM customer WHERE id = ?", String.class,
                customerId);
        return jdbc.queryForObject("INSERT INTO sales_order (order_no, customer_id, customer_code_snapshot,"
                        + " customer_name_snapshot, settle_mode_snapshot, settlement_customer_id,"
                        + " settlement_customer_name_snapshot, seller_id)"
                        + " VALUES (?, ?, ?, ?, 'INDEPENDENT', ?, ?, ?) RETURNING id",
                Long.class, prefix + "-ORDER-" + suffix, customerId, customerCode, prefix + "客户", customerId,
                prefix + "客户", sellerId);
    }

    private Long refund(Long customerId, Long orderId, String suffix) {
        Long intentId = jdbc.queryForObject("INSERT INTO payment_intent (intent_no, customer_id,"
                        + " customer_name_snapshot, source_type, source_id, source_no_snapshot, amount, method, provider)"
                        + " VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, 10, 'ONLINE', 'MOCK') RETURNING id",
                Long.class, prefix + "-INTENT-" + suffix, customerId, prefix + "客户", orderId,
                prefix + "-ORDER-" + suffix);
        return jdbc.queryForObject("INSERT INTO payment_refund (refund_no, intent_id, transaction_id, provider, amount)"
                        + " VALUES (?, ?, ?, 'MOCK', 10) RETURNING id",
                Long.class, prefix + "-REFUND-" + suffix, intentId, 100000L + intentId);
    }

    private CustomerAddForm customerForm() {
        CustomerAddForm form = new CustomerAddForm();
        form.setName(prefix + "客户");
        form.setCustomerTypeId(customerTypeId("ENTERPRISE"));
        form.setSettleMode("INDEPENDENT");
        return form;
    }

    private CustomerUpdateForm updateForm(Long customerId, Integer version) {
        CustomerUpdateForm form = new CustomerUpdateForm();
        form.setCustomerId(customerId);
        form.setVersion(version);
        form.setName(prefix + "改名");
        form.setCustomerTypeId(customerTypeId("ENTERPRISE"));
        form.setSettleMode("INDEPENDENT");
        return form;
    }

    private Long newEmployee(String suffix) {
        String login = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), login, "$argon2id$it-placeholder", "业务员" + suffix);
        return jdbc.queryForObject("SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, login);
    }

    private <T> T as(Long employeeId, Supplier<T> action) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        employee.setDepartmentId(1L);
        SmartRequestUtil.setRequestUser(employee);
        try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
            return action.get();
        }
    }
}
