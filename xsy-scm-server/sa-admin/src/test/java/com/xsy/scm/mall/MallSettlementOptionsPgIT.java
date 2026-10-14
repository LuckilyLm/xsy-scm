package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.order.domain.vo.MallSettlementOptionVO;
import com.xsy.scm.mall.order.service.MallSettlementService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 商城结算方式可用性（真实 PostgreSQL）。
 *
 * <p>
 * 钉死两条此前被审查指出的口径：
 * <ul>
 * <li><b>共享资金不可经结算页泄露</b>：集团共享结算下余额与授信金额一律不返回，与商城钱包 / 账期概览同口径；</li>
 * <li><b>账期可用性不是「有额度就可用」</b>：已用敞口、已确认未开应收订单、逾期与本次占用后是否超限都要参与判定，
 * 避免结算页允许选择、后台确认时又被拒。</li>
 * </ul>
 */
class MallSettlementOptionsPgIT extends ScmW5PgITBase {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal PAYABLE = new BigDecimal("2.4000");

    @Autowired
    private MallSettlementService settlementService;

    @Test
    @DisplayName("集团共享结算：成员与集团主体都读不到共享钱包余额与集团授信，且不能选共享方式")
    void sharedSettlementNeverExposesGroupFunds() {
        Long group = groupCustomer(null);
        Long member = groupCustomer(group);
        // 给集团配上大额授信与共享钱包余额：如果实现漏了口径，这里就会把 88888 / 66666 返给成员
        jdbc.update("UPDATE xsy_customer SET credit_limit = ? WHERE id = ?", new BigDecimal("88888.0000"), group);
        Long account = wallet(group);
        movement(account, group, member, "CREDIT", "66666.0000");
        evictMybatisCache();

        Map<String, MallSettlementOptionVO> memberOptions = options(identity(member));
        assertThat(memberOptions.get("WALLET").getAmountVisible()).as("共享结算不返回余额").isFalse();
        assertThat(memberOptions.get("WALLET").getAvailableAmount()).isNull();
        assertThat(memberOptions.get("WALLET").getEnabled()).as("共享资金需独立授权，默认不可选").isFalse();
        assertThat(memberOptions.get("WALLET").getDisabledReason()).isNotBlank();
        assertThat(memberOptions.get("CREDIT").getAmountVisible()).as("共享结算不返回集团授信").isFalse();
        assertThat(memberOptions.get("CREDIT").getAvailableAmount()).isNull();
        assertThat(memberOptions.get("CREDIT").getEnabled()).isFalse();
        assertThat(memberOptions.get("CREDIT").getDisabledReason()).isNotBlank();
        assertThat(memberOptions.get("COD").getEnabled()).as("货到付款未开通时不可选").isFalse();
        jdbc.update("UPDATE xsy_customer SET cod_allowed = TRUE WHERE id = ?", member);
        evictMybatisCache();
        assertThat(options(identity(member)).get("COD").getEnabled())
                .as("成员获明确授权后可使用货到付款，不需要读取共享资金金额").isTrue();

        // 集团主体自己登录商会也不展示共享总额（与钱包概览的既有规则一致）
        Map<String, MallSettlementOptionVO> groupOptions = options(identity(group));
        assertThat(groupOptions.get("WALLET").getAmountVisible()).isFalse();
        assertThat(groupOptions.get("WALLET").getAvailableAmount()).isNull();
        assertThat(groupOptions.get("CREDIT").getAmountVisible()).isFalse();
        assertThat(groupOptions.get("CREDIT").getAvailableAmount()).isNull();
    }

    @Test
    @DisplayName("账期可用性按正式授信口径：占用、超限与逾期都会让账期不可选")
    void creditOptionFollowsExposureOverdueAndLimit() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("SETTLE-C");
        jdbc.update("UPDATE xsy_customer SET credit_limit = ? WHERE id = ?", new BigDecimal("100.0000"),
                customerId);
        evictMybatisCache();

        // 1. 有额度且无占用：可选，剩余额度即额度本身
        MallSettlementOptionVO free = options(identity(customerId)).get("CREDIT");
        assertThat(free.getEnabled()).isTrue();
        assertThat(free.getAvailableAmount()).isEqualByComparingTo("100.0000");
        assertThat(options(identity(customerId)).get("COD").getEnabled()).as("货到付款默认关闭").isFalse();
        jdbc.update("UPDATE xsy_customer SET cod_allowed = TRUE WHERE id = ?", customerId);
        evictMybatisCache();
        assertThat(options(identity(customerId)).get("COD").getEnabled()).as("后台开通后可选货到付款").isTrue();

        // 2. 已确认但未开应收的订单计入敞口：额度足够时仍可选，剩余额度随之减少。
        //    注意必须先把额度留足，否则确认命令自己的授信校验就会先把这张订单拦下（41201）。
        Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
        assertThat(jdbc.queryForObject("SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?",
                BigDecimal.class, orderId)).isEqualByComparingTo("12.0000");
        MallSettlementOptionVO occupied = options(identity(customerId)).get("CREDIT");
        assertThat(occupied.getEnabled()).isTrue();
        assertThat(occupied.getAvailableAmount()).as("剩余额度 = 额度 - 已确认未开应收").isEqualByComparingTo("88.0000");

        // 3. 把额度压到已用敞口之下：本单再加 2.4 即超限 → 不可选，且不谎报剩余额度
        jdbc.update("UPDATE xsy_customer SET credit_limit = ? WHERE id = ?", new BigDecimal("10.0000"), customerId);
        evictMybatisCache();
        MallSettlementOptionVO overLimit = options(identity(customerId)).get("CREDIT");
        assertThat(overLimit.getEnabled()).as("超出剩余额度不得显示为可选").isFalse();
        assertThat(overLimit.getDisabledReason()).contains("超出");
        assertThat(overLimit.getAvailableAmount()).as("敞口已超过额度，剩余额度收敛为 0").isEqualByComparingTo("0.0000");

        // 4. 恢复额度后改为「逾期」：账期转为不可选，原因是逾期而不是额度
        jdbc.update("UPDATE xsy_customer SET credit_limit = ? WHERE id = ?", new BigDecimal("100.0000"),
                customerId);
        normalReceivable(orderId, "12.0000", LocalDate.now(BUSINESS_ZONE).minusDays(1));
        evictMybatisCache();
        MallSettlementOptionVO overdue = options(identity(customerId)).get("CREDIT");
        assertThat(overdue.getEnabled()).isFalse();
        assertThat(overdue.getDisabledReason()).contains("逾期");
        MallSettlementOptionVO overdueCod = options(identity(customerId)).get("COD");
        assertThat(overdueCod.getEnabled()).as("货到付款不占额度，但逾期风险仍应阻断").isFalse();
        assertThat(overdueCod.getDisabledReason()).contains("逾期");
    }

    @Test
    @DisplayName("独立结算客户：金额可见，余额够则余额可选")
    void independentCustomerSeesOwnFunds() {
        Long customerId = newCustomer();
        Long account = wallet(customerId);
        movement(account, customerId, customerId, "CREDIT", "10.0000");
        evictMybatisCache();

        Map<String, MallSettlementOptionVO> options = options(identity(customerId));
        assertThat(options.get("WALLET").getAmountVisible()).isTrue();
        assertThat(options.get("WALLET").getAvailableAmount()).isEqualByComparingTo("10.0000");
        assertThat(options.get("WALLET").getEnabled()).isTrue();
        assertThat(options.get("CREDIT").getEnabled()).as("未配置授信额度不可选").isFalse();
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private Map<String, MallSettlementOptionVO> options(RequestMallCustomer identity) {
        List<MallSettlementOptionVO> rows = settlementService.options(identity, PAYABLE);
        assertThat(rows).hasSize(3);
        return rows.stream().collect(Collectors.toMap(MallSettlementOptionVO::getMethod, Function.identity()));
    }

    /** 集团（{@code parentId=null}）或集团成员；成员的结算主体指向集团。 */
    private Long groupCustomer(Long parentId) {
        CustomerAddForm form = new CustomerAddForm();
        form.setName(prefix + "-GROUP-" + UUID.randomUUID().toString().substring(0, 5));
        form.setCustomerTypeId(customerTypeId(parentId == null ? "GROUP" : "ENTERPRISE"));
        form.setSettleMode("GROUP");
        form.setSellerId(anyEmployeeId());
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
        OffsetDateTime eventAt = dueDate.minusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        Long id = jdbc.queryForObject("""
                INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,
                    customer_id, customer_name_snapshot, settlement_customer_id,
                    settlement_customer_name_snapshot, entry_type, amount, event_at)
                SELECT ?, 'SALES_ORDER', id, id, customer_id, customer_name_snapshot,
                    settlement_customer_id, settlement_customer_name_snapshot, 'NORMAL', ?, ?
                FROM xsy_sales_order WHERE id = ? RETURNING id
                """, Long.class, "AR-SETTLE-" + UUID.randomUUID(), new BigDecimal(amount), eventAt, orderId);
        jdbc.update("UPDATE xsy_finance_receivable SET due_date = ? WHERE id = ?", dueDate, id);
        return id;
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
                """, "BM-SETTLE-" + UUID.randomUUID(), accountId, settlementCustomerId, customerId, direction,
                new BigDecimal(amount));
    }

    private static RequestMallCustomer identity(Long customerId) {
        RequestMallCustomer customer = new RequestMallCustomer();
        customer.setCustomerId(customerId);
        return customer;
    }
}
