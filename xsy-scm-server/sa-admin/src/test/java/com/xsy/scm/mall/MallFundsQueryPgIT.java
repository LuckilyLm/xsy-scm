package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.funds.domain.form.MallReceivableQueryForm;
import com.xsy.scm.mall.funds.domain.form.MallWalletMovementQueryForm;
import com.xsy.scm.mall.funds.service.MallFundsQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 真实 PostgreSQL 下核对商城资金只读查询的归属、净额与分页口径。 */
class MallFundsQueryPgIT extends ScmW5PgITBase {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private MallFundsQueryService funds;

    @Test
    void groupMembersSeeOnlyTheirOwnMovementsAndReceivables() {
        Long group = groupCustomer(null);
        Long first = groupCustomer(group);
        Long sibling = groupCustomer(group);
        Long skuId = newOnShelfSku("FUNDS-G");
        Long firstReceivable = normalReceivable(confirmedSalesOrder(first, skuId, "1.0000", "1.0000"),
                "30.0000", LocalDate.now(BUSINESS_ZONE).plusDays(2));
        Long siblingReceivable = normalReceivable(confirmedSalesOrder(sibling, skuId, "1.0000", "1.0000"),
                "80.0000", LocalDate.now(BUSINESS_ZONE).plusDays(2));
        Long account = wallet(group);
        movement(account, group, first, "CREDIT", "50.0000");
        movement(account, group, first, "DEBIT", "10.0000");
        movement(account, group, sibling, "CREDIT", "80.0000");

        var firstIdentity = identity(first);
        assertThat(funds.walletSummary(firstIdentity).getBalanceVisible()).isFalse();
        assertThat(funds.walletSummary(firstIdentity).getAvailableBalance()).isNull();
        assertThat(funds.walletSummary(identity(group)).getBalanceVisible()).isFalse();
        var ownMovements = funds.walletMovements(firstIdentity, movementPage(1, 1));
        assertThat(ownMovements.getTotal()).isEqualTo(2L);
        assertThat(ownMovements.getPages()).isEqualTo(2L);
        assertThat(ownMovements.getList()).hasSize(1);
        MallWalletMovementQueryForm creditOnly = movementPage(1, 10);
        creditOnly.setType("CORRECTION");
        creditOnly.setDirection("CREDIT");
        var credits = funds.walletMovements(firstIdentity, creditOnly);
        assertThat(credits.getTotal()).isEqualTo(1L);
        assertThat(credits.getList().getFirst().getAmount()).isEqualByComparingTo("50.0000");

        var ownBills = funds.receivables(firstIdentity, receivablePage(1, 10, null));
        assertThat(ownBills.getTotal()).isEqualTo(1L);
        assertThat(ownBills.getList()).extracting("receivableId").containsExactly(firstReceivable);
        assertThat(funds.creditSummary(firstIdentity).getOpenReceivableAmount()).isEqualByComparingTo("30.0000");
        assertThat(funds.creditSummary(firstIdentity).getCreditLimit()).isNull();
        assertThatThrownBy(() -> funds.receivable(firstIdentity, siblingReceivable))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.FUNDS_RECEIVABLE_NOT_FOUND));
    }

    @Test
    void redAndWriteOffFactsDriveOpenAmountOverdueAndFilteredPages() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("FUNDS-N");
        LocalDate overdueDate = LocalDate.now(BUSINESS_ZONE).minusDays(1);
        Long adjusted = normalReceivable(confirmedSalesOrder(customerId, skuId, "1.0000", "1.0000"),
                "100.0000", overdueDate);
        normalReceivable(confirmedSalesOrder(customerId, skuId, "1.0000", "1.0000"),
                "25.0000", LocalDate.now(BUSINESS_ZONE).plusDays(3));
        redReceivable(adjusted, "20.0000");
        writeOff(adjusted, "30.0000");

        RequestMallCustomer identity = identity(customerId);
        assertThat(funds.walletSummary(identity).getBalanceVisible()).isTrue();
        assertThat(funds.walletSummary(identity).getAvailableBalance()).isEqualByComparingTo("0.0000");
        var detail = funds.receivable(identity, adjusted);
        assertThat(detail.getAmount()).isEqualByComparingTo("100.0000");
        assertThat(detail.getRedAmount()).isEqualByComparingTo("20.0000");
        assertThat(detail.getWrittenOffAmount()).isEqualByComparingTo("30.0000");
        assertThat(detail.getOpenAmount()).isEqualByComparingTo("50.0000");
        assertThat(detail.getSettleState()).isEqualTo("PARTIAL");
        assertThat(detail.getOverdue()).isTrue();
        assertThat(funds.creditSummary(identity).getOpenReceivableAmount()).isEqualByComparingTo("75.0000");
        assertThat(funds.creditSummary(identity).getEarliestOverdueDate()).isEqualTo(overdueDate);

        var firstPage = funds.receivables(identity, receivablePage(1, 1, null));
        assertThat(firstPage.getTotal()).isEqualTo(2L);
        assertThat(firstPage.getPages()).isEqualTo(2L);
        assertThat(firstPage.getList()).hasSize(1);
        var secondPage = funds.receivables(identity, receivablePage(2, 1, null));
        assertThat(secondPage.getList()).hasSize(1);
        assertThat(secondPage.getList().getFirst().getReceivableId())
                .isNotEqualTo(firstPage.getList().getFirst().getReceivableId());
        assertThat(funds.receivables(identity, receivablePage(1, 10, "PARTIAL")).getList())
                .extracting("receivableId").containsExactly(adjusted);
        assertThat(funds.receivables(identity, receivablePage(1, 10, "SETTLED")).getList()).isEmpty();
    }

    private Long groupCustomer(Long parentId) {
        CustomerAddForm form = new CustomerAddForm();
        form.setName(prefix + "-GROUP-" + UUID.randomUUID().toString().substring(0, 5));
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

    private Long normalReceivable(Long orderId, String amount, LocalDate dueDate) {
        return jdbc.queryForObject("""
                INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,
                    customer_id, customer_name_snapshot, settlement_customer_id,
                    settlement_customer_name_snapshot, entry_type, amount, event_at, due_date)
                SELECT ?, 'SALES_ORDER', id, id, customer_id, customer_name_snapshot,
                    settlement_customer_id, settlement_customer_name_snapshot, 'NORMAL', ?, now(), ?
                FROM xsy_sales_order WHERE id = ? RETURNING id
                """, Long.class, "AR-FUNDS-" + UUID.randomUUID(), new BigDecimal(amount), dueDate, orderId);
    }

    private void redReceivable(Long originalId, String amount) {
        jdbc.update("""
                INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,
                    customer_id, customer_name_snapshot, settlement_customer_id,
                    settlement_customer_name_snapshot, entry_type, original_receivable_id, amount,
                    event_at, due_date, reason)
                SELECT ?, 'ORDER_RETURN', ?, order_id,
                    customer_id, customer_name_snapshot, settlement_customer_id,
                    settlement_customer_name_snapshot, 'RED', id, ?, now(), due_date, '测试红字'
                FROM xsy_finance_receivable WHERE id = ?
                """, "AR-FUNDS-RED-" + UUID.randomUUID(),
                UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE, new BigDecimal(amount), originalId);
    }

    private void writeOff(Long receivableId, String amount) {
        jdbc.update("""
                INSERT INTO xsy_finance_write_off (write_off_no, source_type, source_id, target_type,
                    target_id, amount, entry_type, written_off_at, operator)
                VALUES (?, 'RECEIPT', ?, 'RECEIVABLE', ?, ?, 'NORMAL', now(), 'PG-IT')
                """, "WO-FUNDS-" + UUID.randomUUID(), receivableId, receivableId, new BigDecimal(amount));
    }

    private Long wallet(Long settlementCustomerId) {
        return jdbc.queryForObject("""
                INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot)
                SELECT id, name FROM xsy_customer WHERE id = ? RETURNING id
                """, Long.class, settlementCustomerId);
    }

    private void movement(Long accountId, Long settlementCustomerId, Long customerId, String direction,
            String amount) {
        jdbc.update("""
                INSERT INTO xsy_customer_balance_movement (movement_no, account_id, settlement_customer_id,
                    customer_id, type, direction, amount, reason, occurred_at)
                VALUES (?, ?, ?, ?, 'CORRECTION', ?, ?, '测试调整', now())
                """, "BM-FUNDS-" + UUID.randomUUID(), accountId, settlementCustomerId, customerId, direction,
                new BigDecimal(amount));
    }

    private static RequestMallCustomer identity(Long customerId) {
        RequestMallCustomer customer = new RequestMallCustomer();
        customer.setCustomerId(customerId);
        return customer;
    }

    private static MallWalletMovementQueryForm movementPage(long pageNum, long pageSize) {
        MallWalletMovementQueryForm form = new MallWalletMovementQueryForm();
        form.setPageNum(pageNum);
        form.setPageSize(pageSize);
        return form;
    }

    private static MallReceivableQueryForm receivablePage(long pageNum, long pageSize, String state) {
        MallReceivableQueryForm form = new MallReceivableQueryForm();
        form.setPageNum(pageNum);
        form.setPageSize(pageSize);
        form.setSettleState(state);
        return form;
    }
}
