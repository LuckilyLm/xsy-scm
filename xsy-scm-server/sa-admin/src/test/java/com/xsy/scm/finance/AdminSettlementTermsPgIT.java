package com.xsy.scm.finance;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.finance.domain.form.FinanceReceiptAddForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddItemForm;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.finance.service.FinanceWriteOffService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminSettlementTermsPgIT extends ScmW5PgITBase {
    @Autowired private FinanceReceiptService financeReceiptService;
    @Autowired private FinanceWriteOffService financeWriteOffService;
    @Autowired private com.xsy.scm.order.service.OrderCreditService orderCreditService;

    private String key() { return UUID.randomUUID().toString(); }

    private Long groupCustomer(Long parent) {
        CustomerAddForm form = new CustomerAddForm();
        form.setCustomerCode(prefix + key().substring(0, 5));
        form.setName("集团结算验收");
        form.setCustomerTypeId(customerTypeId(parent == null ? "GROUP" : "ENTERPRISE"));
        form.setSettleMode("GROUP");
        form.setSellerId(1L);
        form.setParentCustomerId(parent);
        form.setSettlementCustomerId(parent);
        Long id = customerService.add(form);
        CustomerStatusForm status = new CustomerStatusForm();
        status.setCustomerId(id);
        status.setVersion(customerService.require(id).getVersion());
        status.setStatus("COOPERATING");
        customerService.updateStatus(status);
        return id;
    }

    private Long receivable(Long customerId, String eventAt) {
        Long skuId = newOnShelfSku(key().substring(0, 4));
        Long orderId = confirmedSalesOrder(customerId, skuId, "1.0000", "1.0000");
        // Isolate due-date and allocation semantics with a source snapshot from a real order.
        return jdbc.queryForObject("INSERT INTO finance_receivable (receivable_no,source_type,source_id,order_id,"
                + "customer_id,customer_name_snapshot,settlement_customer_id,settlement_customer_name_snapshot,entry_type,amount,event_at)"
                + " SELECT ?, 'SALES_ORDER', id,id,customer_id,customer_name_snapshot,settlement_customer_id,"
                + "settlement_customer_name_snapshot,'NORMAL',10,?::timestamptz FROM sales_order WHERE id=? RETURNING id",
                Long.class, "AR-" + key(), eventAt, orderId);
    }

    private Long receipt(Long customerId) {
        FinanceReceiptAddForm form = new FinanceReceiptAddForm();
        form.setCustomerId(customerId);
        form.setAmount("10.0000");
        form.setMethod("BANK_TRANSFER");
        form.setReceivedAt(OffsetDateTime.now());
        return financeReceiptService.add(form, key()).getReceiptId();
    }

    private FinanceWriteOffAddForm allocation(Long receiptId, Long receivableId) {
        FinanceWriteOffAddItemForm item = new FinanceWriteOffAddItemForm();
        item.setTargetId(receivableId);
        item.setAmount("10.0000");
        FinanceWriteOffAddForm form = new FinanceWriteOffAddForm();
        form.setSourceType("RECEIPT");
        form.setSourceId(receiptId);
        form.setItems(List.of(item));
        return form;
    }

    @Test void sameSettlementAllowsSiblingAllocationAndReleasesCredit() {
        Long parent = groupCustomer(null);
        Long payer = groupCustomer(parent);
        Long debtor = groupCustomer(parent);
        Long receivable = receivable(debtor, OffsetDateTime.now().toString());
        Long receipt = receipt(payer);
        var form = allocation(receipt, receivable);
        String key = key();
        var result = financeWriteOffService.add(form, key);
        assertThat(financeWriteOffService.add(form, key).getItems().getFirst().getWriteOffId())
                .isEqualTo(result.getItems().getFirst().getWriteOffId());
        assertThat(orderCreditService.check(parent, BigDecimal.ZERO).getOpenReceivableAmount()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT customer_id FROM finance_receipt WHERE id=?",Long.class,receipt))
                .as("收款保留实际付款客户").isEqualTo(payer);
        jdbc.update("UPDATE customer SET settlement_customer_id=id WHERE id=?", payer);
        assertThat(jdbc.queryForObject("SELECT settlement_customer_id FROM finance_receipt WHERE id=?",Long.class,receipt))
                .as("统一收款主体冻结为集团结算客户").isEqualTo(parent);
    }

    @Test void siblingCannotBeConfiguredAsSettlementCustomer() {
        Long parent = groupCustomer(null);
        Long sibling = groupCustomer(parent);
        CustomerAddForm form = new CustomerAddForm();
        form.setCustomerCode(prefix + key().substring(0, 5));
        form.setName("非法兄弟结算客户");
        form.setCustomerTypeId(customerTypeId("ENTERPRISE"));
        form.setSettleMode("GROUP");
        form.setParentCustomerId(parent);
        form.setSettlementCustomerId(sibling);
        expectCode(() -> customerService.add(form), 40032);
    }

    @Test void nonGroupParentCannotActAsSettlementCustomerEvenWithDirtyRelationship() {
        Long parent = newCustomer();
        Long child = newCustomer();
        jdbc.update("UPDATE customer SET settle_mode='GROUP',parent_customer_id=?,settlement_customer_id=? WHERE id=?",
                parent, parent, child);
        jdbc.update("UPDATE customer SET settle_mode='GROUP' WHERE id=?", parent);
        evictMybatisCache();
        assertThatThrownBy(() -> receipt(child)).isInstanceOf(com.xsy.scm.common.exception.ScmBusinessException.class);
    }

    @Test void sourceCustomerScopeCannotBeReplacedByGroupRelationship() {
        Long parent = groupCustomer(null);
        Long payer = groupCustomer(parent);
        Long debtor = groupCustomer(parent);
        Long receivable = receivable(debtor, OffsetDateTime.now().toString());
        Long receipt = receipt(payer);
        jdbc.update("UPDATE customer SET seller_id=987654321 WHERE id=?", payer);
        evictMybatisCache();
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setAdministratorFlag(false);
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        SmartRequestUtil.setRequestUser(employee);
        assertThatThrownBy(() -> financeWriteOffService.add(allocation(receipt, receivable), key()))
                .isInstanceOf(ScmDataScopeException.class);
    }

    @Test void dueDateFreezesAtShanghaiEventDateAndDoesNotFollowMasterEdits() {
        Long customer = newCustomer();
        jdbc.update("UPDATE customer SET credit_period_type='BY_TIME',credit_period_value=10,credit_period_unit='DAY' WHERE id=?",customer);
        evictMybatisCache();
        Long receivable = receivable(customer, "2026-01-10T20:00:00Z");
        assertThat(jdbc.queryForObject("SELECT due_date FROM finance_receivable WHERE id=?",LocalDate.class,receivable))
                .isEqualTo(LocalDate.of(2026,1,21));
        jdbc.update("UPDATE customer SET credit_period_value=30 WHERE id=?",customer);
        evictMybatisCache();
        assertThat(jdbc.queryForObject("SELECT due_date FROM finance_receivable WHERE id=?",LocalDate.class,receivable))
                .isEqualTo(LocalDate.of(2026,1,21));
        assertThat(orderCreditService.check(customer, BigDecimal.ZERO).getOverdue()).isTrue();
    }
}
