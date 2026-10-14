package com.xsy.scm.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.payment.domain.form.RefundFundingExecuteForm;
import com.xsy.scm.payment.service.PaymentRefundRecoveryService;
import com.xsy.scm.payment.service.RefundFundingAllocationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 混合资金来源下「单条来源失败」的保存点隔离与修复后重试（PG IT）。
 *
 * <p><b>为什么要这一组用例</b>：{@code RefundFundingAllocationService.executeRows} 对每条来源单独捕获异常并把该行记为
 * {@code FAILED}，但余额返还与在线退款准备这两个子入口原先用 {@code MANDATORY} 参加外层事务。子调用一旦抛出异常，
 * 事务拦截器会把<b>整笔外层事务</b>标记为 rollback-only，外层随后写入的失败状态与幂等结果在提交时被一并丢弃，
 * 表现为 {@code UnexpectedRollbackException}。改成 {@code NESTED}（同一连接的 JDBC 保存点）后，失败只回滚该来源自己的写入。
 *
 * <p>这两件事只有真实数据库能证明：保存点是连接级语义，而「整笔回滚」只在提交那一刻才暴露。
 *
 * <p><b>为什么必须有在线腿</b>：余额腿失败发生在读账户之前，不产生任何写入，回滚与否看不出差别；
 * 在线腿会先插入 {@code xsy_payment_refund} 再向渠道注册表取实现，取不到实现才抛错 ——
 * 这条路径有真实写入，保存点回滚掉什么可以被逐行核对。
 *
 * <p><b>夹具为什么直接插分摊行</b>：计划由 {@code freeze} 按订单资金来源生成，而混合资金订单还要为在线腿补财务收款单。
 * 本类钉的是「单条来源失败时外层事务的边界」，把「已冻结的余额 + 在线分摊行」作为输入固定下来，
 * 不重复验证冻结与资金完整性校验（那两组另有覆盖）。
 */
@DisplayName("退款分摊单来源失败的保存点隔离与重试（PG IT）")
class RefundFundingAllocationSavepointPgIT extends ScmW5PgITBase {

    @Autowired
    private RefundFundingAllocationService refundFundingAllocationService;

    @Autowired
    private PaymentRefundRecoveryService paymentRefundRecoveryService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("在线来源取不到渠道实现时只回滚该来源的写入，余额腿与失败状态仍提交")
    void onlinePreparationFailureRollsBackOnlyItsOwnWrites() {
        // WECHAT 已冻结契约但尚无实现：退款单能建出来，向渠道注册表取实现时抛「不支持的渠道」。
        MixedFundingFixture fixture = completedRefundWithMixedFunding("WECHAT", "SUCCEEDED");

        RefundFundingExecuteForm form = new RefundFundingExecuteForm();
        form.setRefundId(fixture.orderRefundId());
        String key = prefix + ":savepoint-online:" + UUID.randomUUID();

        var result = refundFundingAllocationService.execute(form, key);

        assertThat(result.getStatus()).isEqualTo("FAILED");

        assertThat(allocationStatus(fixture.balanceAllocationId()))
                .as("先成功的余额腿不能被后失败来源的回滚带走")
                .isEqualTo("SUCCEEDED");
        assertThat(balanceReturnCount(fixture.balanceAllocationId()))
                .as("余额腿的返还流水必须已经提交，且只有一条")
                .isEqualTo(1);

        assertThat(allocationStatus(fixture.onlineAllocationId())).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT failure_message FROM xsy_refund_funding_allocation WHERE id = ?",
                String.class, fixture.onlineAllocationId()))
                .as("失败必须发生在渠道退款单已经建出来之后，否则「保存点回滚掉了写入」这句话无从谈起")
                .isEqualTo("不支持的支付渠道");
        assertThat(paymentRefundCount(fixture.onlineAllocationId()))
                .as("保存点必须把在线腿已经插入的渠道退款单回滚掉，不能留下没有对应分摊的孤行")
                .isZero();

        assertThat(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM xsy_idempotency_record"
                + " WHERE idempotency_key = ? AND result_data IS NOT NULL)", Boolean.class, key))
                .as("外层事务必须正常提交，失败状态与幂等结果都要落库")
                .isTrue();
        assertThat(financePaymentCount(fixture.orderRefundId()))
                .as("来源没有全部成功，不登记财务付款")
                .isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("失败来源修复后重试：余额腿不重复返还，在线腿补齐后整单完成且财务付款唯一")
    void retryAfterRepairingTheFailedSourceCompletesTheRefundExactlyOnce() {
        // 在线腿的交易此时还没成功，退款准备必然失败；修复后重试应能接着往下走。
        MixedFundingFixture fixture = completedRefundWithMixedFunding("MOCK", "PENDING");

        RefundFundingExecuteForm form = new RefundFundingExecuteForm();
        form.setRefundId(fixture.orderRefundId());

        var first = refundFundingAllocationService.execute(form, prefix + ":savepoint-retry-a:" + UUID.randomUUID());
        assertThat(first.getStatus()).isEqualTo("FAILED");
        assertThat(allocationStatus(fixture.balanceAllocationId())).isEqualTo("SUCCEEDED");
        assertThat(allocationStatus(fixture.onlineAllocationId())).isEqualTo("FAILED");

        jdbc.update("UPDATE xsy_payment_transaction SET status = 'SUCCEEDED', paid_at = CURRENT_TIMESTAMP"
                + " WHERE id = ?", fixture.onlineTransactionId());

        var second = refundFundingAllocationService.execute(form, prefix + ":savepoint-retry-b:" + UUID.randomUUID());
        assertThat(second.getStatus())
                .as("余额腿已成功、在线腿停在渠道处理中")
                .isEqualTo("PROCESSING");
        assertThat(balanceReturnCount(fixture.balanceAllocationId()))
                .as("重试不能把已经返还过的余额腿再返一次")
                .isEqualTo(1);
        Long paymentRefundId = jdbc.queryForObject("SELECT id FROM xsy_payment_refund"
                + " WHERE source_type = 'REFUND_FUNDING_ALLOCATION' AND source_id = ?", Long.class,
                fixture.onlineAllocationId());
        assertThat(paymentRefundId).as("重试要为在线腿建立渠道退款意图").isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM xsy_payment_refund WHERE id = ?", String.class,
                paymentRefundId)).isEqualTo("PROCESSING");
        assertThat(financePaymentCount(fixture.orderRefundId())).isZero();

        paymentRefundRecoveryService.recover(paymentRefundId, operator(), "失败来源修复后重试", true);

        assertThat(allocationStatus(fixture.balanceAllocationId())).isEqualTo("SUCCEEDED");
        assertThat(allocationStatus(fixture.onlineAllocationId())).isEqualTo("SUCCEEDED");
        assertThat(balanceReturnCount(fixture.balanceAllocationId()))
                .as("整单完成后余额腿仍只有一条返还流水")
                .isEqualTo(1);
        assertThat(paymentRefundCount(fixture.onlineAllocationId()))
                .as("在线腿仍只有一张渠道退款单，重试不另建")
                .isEqualTo(1);
        assertThat(financePaymentCount(fixture.orderRefundId()))
                .as("全部来源成功后自动登记且只登记一笔财务付款")
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private record MixedFundingFixture(Long orderRefundId, Long orderId, Long balanceAllocationId,
            Long onlineAllocationId, Long onlineTransactionId) {
    }

    /**
     * 一张「已完成的退款单 + 余额腿与在线腿各一条待执行分摊行」的完整前置。
     *
     * <p>订单资金来源按真实形态补齐（两条腿各有 SUCCEEDED 的意图与交易、余额腿另有消费流水），
     * 因为分摊执行入口会锁订单资金；在线腿的交易状态由参数决定，用来制造「渠道那笔钱还没到账」的退款准备失败。
     */
    private MixedFundingFixture completedRefundWithMixedFunding(String onlineProvider,
            String onlineTransactionStatus) {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("savepoint");
        Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
        String orderNo = jdbc.queryForObject("SELECT order_no FROM xsy_sales_order WHERE id = ?", String.class,
                orderId);
        BigDecimal refundAmount = jdbc.queryForObject(
                "SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?", BigDecimal.class, orderId);
        Long settlementCustomerId = jdbc.queryForObject(
                "SELECT settlement_customer_id FROM xsy_sales_order WHERE id = ?", Long.class, orderId);
        String settlementCustomerName = jdbc.queryForObject(
                "SELECT settlement_customer_name_snapshot FROM xsy_sales_order WHERE id = ?", String.class, orderId);
        BigDecimal balanceAllocated = refundAmount.multiply(new BigDecimal("0.4")).setScale(4, RoundingMode.HALF_UP);
        BigDecimal onlineAllocated = refundAmount.subtract(balanceAllocated);

        Long accountId = jdbc.queryForObject("""
                INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot,
                    created_by, updated_by)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, settlementCustomerId, settlementCustomerName, operator(), operator());

        // 余额腿：ck_payment_intent_method_provider 要求 BALANCE 只能配 INTERNAL_BALANCE 且来源为销售订单。
        Long balanceIntentId = jdbc.queryForObject("""
                INSERT INTO xsy_payment_intent
                    (intent_no, customer_id, customer_name_snapshot, source_type, source_id, source_no_snapshot,
                     amount, method, provider, status, succeeded_at, created_by, updated_by)
                VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, CAST(? AS numeric), 'BALANCE', 'INTERNAL_BALANCE', 'SUCCEEDED',
                    CURRENT_TIMESTAMP, ?, ?)
                RETURNING id
                """, Long.class, prefix + "-SP-BI-" + UUID.randomUUID().toString().substring(0, 8), customerId,
                settlementCustomerName, orderId, orderNo, balanceAllocated, operator(), operator());

        Long balanceTransactionId = jdbc.queryForObject("""
                INSERT INTO xsy_payment_transaction
                    (transaction_no, intent_id, provider, provider_transaction_no, amount, provider_amount, status,
                     paid_at, created_by)
                VALUES (?, ?, 'INTERNAL_BALANCE', ?, CAST(? AS numeric), CAST(? AS numeric), 'SUCCEEDED',
                    CURRENT_TIMESTAMP, ?)
                RETURNING id
                """, Long.class, prefix + "-SP-BT-" + UUID.randomUUID().toString().substring(0, 8), balanceIntentId,
                prefix + "-SP-BPTN-" + UUID.randomUUID().toString().substring(0, 8), balanceAllocated, balanceAllocated,
                operator());

        Long balanceMovementId = jdbc.queryForObject("""
                INSERT INTO xsy_customer_balance_movement
                    (movement_no, account_id, settlement_customer_id, customer_id, type, direction, amount,
                     source_type, source_id, reason, occurred_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, 'CONSUME', 'DEBIT', CAST(? AS numeric), 'PAYMENT_INTENT', ?, '余额支付',
                    CURRENT_TIMESTAMP, ?, ?)
                RETURNING id
                """, Long.class, prefix + "-SP-BM-" + UUID.randomUUID().toString().substring(0, 8), accountId,
                settlementCustomerId, customerId, balanceAllocated, balanceIntentId, operator(), operator());

        Long onlineIntentId = jdbc.queryForObject("""
                INSERT INTO xsy_payment_intent
                    (intent_no, customer_id, customer_name_snapshot, source_type, source_id, source_no_snapshot,
                     amount, method, provider, status, succeeded_at, created_by, updated_by)
                VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, CAST(? AS numeric), 'ONLINE', ?, 'SUCCEEDED',
                    CURRENT_TIMESTAMP, ?, ?)
                RETURNING id
                """, Long.class, prefix + "-SP-OI-" + UUID.randomUUID().toString().substring(0, 8), customerId,
                settlementCustomerName, orderId, orderNo, onlineAllocated, onlineProvider, operator(), operator());

        // ck_payment_transaction_paid_pairing：只有 SUCCEEDED 才允许带 paid_at。
        Long onlineTransactionId = jdbc.queryForObject("""
                INSERT INTO xsy_payment_transaction
                    (transaction_no, intent_id, provider, provider_transaction_no, amount, provider_amount, status,
                     paid_at, created_by)
                VALUES (?, ?, ?, ?, CAST(? AS numeric), CAST(? AS numeric), ?, %s, ?)
                RETURNING id
                """.formatted("SUCCEEDED".equals(onlineTransactionStatus) ? "CURRENT_TIMESTAMP" : "NULL"), Long.class,
                prefix + "-SP-OT-" + UUID.randomUUID().toString().substring(0, 8), onlineIntentId, onlineProvider,
                prefix + "-SP-OPTN-" + UUID.randomUUID().toString().substring(0, 8), onlineAllocated, onlineAllocated,
                onlineTransactionStatus, operator());

        // ck_order_refund_completion：COMPLETED 必须带 completed_at；return_id 全表唯一，与 id 共用序列。
        Long orderRefundId = jdbc.queryForObject("""
                INSERT INTO xsy_order_refund
                    (refund_no, return_id, order_id, customer_id, refund_amount, status, completed_at, created_by)
                VALUES (?, nextval('xsy_v2.order_refund_id_seq'), ?, ?, CAST(? AS numeric), 'COMPLETED',
                    CURRENT_TIMESTAMP, ?)
                RETURNING id
                """, Long.class, prefix + "-SP-AOR-" + UUID.randomUUID().toString().substring(0, 8), orderId,
                customerId, refundAmount, operator());

        Long balanceAllocationId = insertAllocation(orderRefundId, orderId, customerId, settlementCustomerId,
                "BALANCE", balanceMovementId, balanceAllocated, 0);
        Long onlineAllocationId = insertAllocation(orderRefundId, orderId, customerId, settlementCustomerId, "ONLINE",
                onlineTransactionId, onlineAllocated, 1);

        return new MixedFundingFixture(orderRefundId, orderId, balanceAllocationId, onlineAllocationId,
                onlineTransactionId);
    }

    private Long insertAllocation(Long orderRefundId, Long orderId, Long customerId, Long settlementCustomerId,
            String sourceMethod, Long sourceId, BigDecimal allocatedAmount, int sortOrder) {
        return jdbc.queryForObject("""
                INSERT INTO xsy_refund_funding_allocation
                    (order_refund_id, order_id, customer_id, settlement_customer_id, source_method, source_id,
                     source_principal, allocated_amount, status, source_succeeded_at, sort_order, created_by,
                     updated_by)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS numeric), CAST(? AS numeric), 'PENDING',
                    CURRENT_TIMESTAMP, ?, ?, ?)
                RETURNING id
                """, Long.class, orderRefundId, orderId, customerId, settlementCustomerId, sourceMethod, sourceId,
                allocatedAmount, allocatedAmount, sortOrder, operator(), operator());
    }

    private String allocationStatus(Long allocationId) {
        return jdbc.queryForObject("SELECT status FROM xsy_refund_funding_allocation WHERE id = ?", String.class,
                allocationId);
    }

    private Integer balanceReturnCount(Long allocationId) {
        return jdbc.queryForObject("SELECT count(*) FROM xsy_customer_balance_movement"
                + " WHERE source_type = 'REFUND_FUNDING_ALLOCATION' AND source_id = ?", Integer.class, allocationId);
    }

    private Integer paymentRefundCount(Long allocationId) {
        return jdbc.queryForObject("SELECT count(*) FROM xsy_payment_refund"
                + " WHERE source_type = 'REFUND_FUNDING_ALLOCATION' AND source_id = ?", Integer.class, allocationId);
    }

    private Integer financePaymentCount(Long orderRefundId) {
        return jdbc.queryForObject("SELECT count(*) FROM xsy_finance_payment"
                + " WHERE source_type = 'ORDER_REFUND' AND source_id = ? AND entry_type = 'NORMAL'", Integer.class,
                orderRefundId);
    }

    private String operator() {
        return "IT-ALLOC-SAVEPOINT";
    }
}
