package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.mall.account.domain.form.MallAccountCreateForm;
import com.xsy.scm.mall.account.service.MallAccountService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 仅用于隔离库页面联调；必须显式启用，普通回归不会执行。 */
class MallFundsUiFixtureManual extends ScmW5PgITBase {
    @Autowired
    private MallAccountService accountService;

    @Test
    @EnabledIfSystemProperty(named = "scm.mall.funds.ui-fixture", matches = "true")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void createDisposableAccounts() throws IOException {
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        assertThat(database).startsWith("xsy_verify_funds_");

        Long independent = newCustomer();
        Long group = groupCustomer(null);
        Long memberA = groupCustomer(group);
        Long memberB = groupCustomer(group);
        setSevenDayCredit(independent);
        setSevenDayCredit(group);

        Long skuId = newOnShelfSku("FUNDS-UI");
        Long independentOrder = mallOrder(independent, skuId, "1.0000");
        Long memberAOrder = mallOrder(memberA, skuId, "2.0000");
        Long memberBOrder = mallOrder(memberB, skuId, "3.0000");
        Long independentBill = receivable(independentOrder);
        Long memberABill = receivable(memberAOrder);
        Long memberBBill = receivable(memberBOrder);

        Long ownWallet = wallet(independent);
        movement(ownWallet, independent, independent, "50.0000");
        Long sharedWallet = wallet(group);
        movement(sharedWallet, group, memberA, "20.0000");
        movement(sharedWallet, group, memberB, "30.0000");

        Map<String, Object> accounts = new LinkedHashMap<>();
        accounts.put("independent", account(independent, independentOrder, independentBill, "50.0000"));
        accounts.put("group", account(group, null, null, null));
        accounts.put("memberA", account(memberA, memberAOrder, memberABill, "20.0000"));
        accounts.put("memberB", account(memberB, memberBOrder, memberBBill, "30.0000"));
        Path output = Path.of("../.runtime/funds-ui-accounts.json").toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.writeString(output, objectMapper.writeValueAsString(accounts));
    }

    private Long groupCustomer(Long parentId) {
        CustomerAddForm form = new CustomerAddForm();
        form.setName(prefix + "-" + UUID.randomUUID().toString().substring(0, 5));
        form.setCustomerTypeId(customerTypeId(parentId == null ? "GROUP" : "ENTERPRISE"));
        form.setSettleMode("GROUP");
        form.setSellerId(1L);
        form.setParentCustomerId(parentId);
        form.setSettlementCustomerId(parentId);
        Long id = customerService.add(form);
        CustomerStatusForm status = new CustomerStatusForm();
        status.setCustomerId(id);
        status.setVersion(customerService.require(id).getVersion());
        status.setStatus("COOPERATING");
        customerService.updateStatus(status);
        return id;
    }

    private void setSevenDayCredit(Long customerId) {
        jdbc.update("UPDATE xsy_customer SET credit_period_type = 'BY_TIME', credit_period_value = 7,"
                + " credit_period_unit = 'DAY', updated_at = now() WHERE id = ?", customerId);
    }

    private Long mallOrder(Long customerId, Long skuId, String quantity) {
        Long orderId = confirmedSalesOrder(customerId, skuId, quantity, quantity);
        // 订单主体和明细仍由正式命令建立；只为商城订单详情的客户归属查询标记来源。
        assertThat(jdbc.update("UPDATE xsy_sales_order SET order_source = 'MALL' WHERE id = ?", orderId)).isEqualTo(1);
        return orderId;
    }

    private Long receivable(Long orderId) {
        return jdbc.queryForObject("""
                INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,
                    customer_id, customer_name_snapshot, settlement_customer_id,
                    settlement_customer_name_snapshot, entry_type, amount, event_at)
                SELECT ?, 'SALES_ORDER', id, id, customer_id, customer_name_snapshot,
                    settlement_customer_id, settlement_customer_name_snapshot, 'NORMAL',
                    settlement_total_amount, now()
                FROM xsy_sales_order WHERE id = ? RETURNING id
                """, Long.class, "AR-UI-" + UUID.randomUUID(), orderId);
    }

    private Long wallet(Long settlementCustomerId) {
        return jdbc.queryForObject("""
                INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot)
                SELECT id, name FROM xsy_customer WHERE id = ? RETURNING id
                """, Long.class, settlementCustomerId);
    }

    private void movement(Long accountId, Long settlementCustomerId, Long customerId, String amount) {
        jdbc.update("""
                INSERT INTO xsy_customer_balance_movement (movement_no, account_id, settlement_customer_id,
                    customer_id, type, direction, amount, reason, occurred_at)
                VALUES (?, ?, ?, ?, 'CORRECTION', 'CREDIT', ?, '隔离页面联调夹具', now())
                """, "BM-UI-" + UUID.randomUUID(), accountId, settlementCustomerId, customerId,
                new BigDecimal(amount));
    }

    private Map<String, Object> account(Long customerId, Long orderId, Long receivableId, String ownMovementAmount) {
        String phone = "139" + String.format("%08d", Integer.toUnsignedLong(UUID.randomUUID().hashCode())
                % 100_000_000);
        String temporaryPassword = "FundsUi@" + UUID.randomUUID().toString().substring(0, 8);
        String newPassword = "FundsUi@" + UUID.randomUUID().toString().substring(0, 8);
        MallAccountCreateForm form = new MallAccountCreateForm();
        form.setCustomerId(customerId);
        form.setLoginPhone(phone);
        form.setTemporaryPassword(temporaryPassword);
        accountService.create(form);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("phone", phone);
        result.put("temporaryPassword", temporaryPassword);
        result.put("newPassword", newPassword);
        result.put("orderId", orderId);
        result.put("receivableId", receivableId);
        result.put("receivableAmount", orderId == null ? null
                : jdbc.queryForObject("SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?",
                        BigDecimal.class, orderId).toPlainString());
        result.put("ownMovementAmount", ownMovementAmount);
        return result;
    }
}
