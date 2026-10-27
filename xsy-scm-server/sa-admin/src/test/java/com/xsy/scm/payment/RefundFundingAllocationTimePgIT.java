package com.xsy.scm.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.mall.aftersale.dao.MallAfterSaleFundingSourceDao;
import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleFundingTimeVO;
import com.xsy.scm.payment.domain.form.RefundFundingExecuteForm;
import com.xsy.scm.payment.service.RefundFundingAllocationService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 余额分摊返还的资金发生时间与单条失败的持久化边界（PG IT）。
 *
 * <p><b>为什么要这一组用例</b>：{@code xsy_customer_balance_movement.occurred_at} 同时是客户余额流水的
 * 时间轴、商城售后「资金到账时间」的取数来源（{@code MallAfterSaleFundingSourceDao.allocationSuccessTimes}）。
 * 余额分摊只由人工与重试入口触发，钱包真正加钱发生在执行那一刻，与退款单的业务完成时刻可以相隔很久；
 * 若把两者写成同一个值，客户流水与商城都会显示成退款单完成时间，按日期对账与流水排序随之失真。
 *
 * <p><b>夹具为什么直接插分摊行</b>：计划由 {@code freeze} 按订单资金来源生成，而真实的余额分摊只出现在
 * 「余额 + 在线」的混合资金订单上（纯余额订单在退款完成时就由旧钱包路径自动返还，混合资金才需要人工执行分摊）。
 * 造一张混合资金订单还要为在线那一腿补财务收款单，而那与被测对象无关 —— 本类钉的是余额腿写入
 * {@code occurred_at} 的口径，因此把「已冻结的余额分摊行」作为输入固定下来。
 */
@DisplayName("余额分摊返还时间与失败提交（PG IT）")
class RefundFundingAllocationTimePgIT extends ScmW5PgITBase {

    @Autowired
    private RefundFundingAllocationService refundFundingAllocationService;

    @Autowired
    private MallAfterSaleFundingSourceDao mallAfterSaleFundingSourceDao;

    @Test
    @DisplayName("退款单先完成、稍后再执行余额返还时，流水记的是实际返还时刻而不是退款完成时刻")
    void balanceReturnRecordsTheExecutionTimeNotTheRefundCompletionTime() {
        BalanceAllocationFixture fixture = completedRefundWithBalanceAllocation();

        // 模拟「后台 09:00 确认退款完成，11:00 才执行余额返还」：把退款单的完成时刻推到两小时前。
        jdbc.update("UPDATE xsy_order_refund SET completed_at = CURRENT_TIMESTAMP - INTERVAL '2 hours' WHERE id = ?",
                fixture.orderRefundId());
        OffsetDateTime refundCompletedAt = jdbc.queryForObject(
                "SELECT completed_at FROM xsy_order_refund WHERE id = ?", OffsetDateTime.class,
                fixture.orderRefundId());

        RefundFundingExecuteForm form = new RefundFundingExecuteForm();
        form.setRefundId(fixture.orderRefundId());
        refundFundingAllocationService.execute(form, prefix + ":alloc:" + UUID.randomUUID());

        OffsetDateTime occurredAt = jdbc.queryForObject(
                "SELECT occurred_at FROM xsy_customer_balance_movement WHERE source_type = 'REFUND_FUNDING_ALLOCATION'"
                        + " AND source_id = ?",
                OffsetDateTime.class, fixture.allocationId());

        assertThat(occurredAt)
                .as("流水时间必须晚于退款单完成时刻，否则它记的是业务完成时间而不是资金实际返还时间")
                .isAfter(refundCompletedAt.plusMinutes(1));
        assertThat(Duration.between(occurredAt, OffsetDateTime.now()).abs())
                .as("流水时间应当就是本次执行的时刻（留 2 分钟余量吸收时钟与执行开销）")
                .isLessThan(Duration.ofMinutes(2));

        // 商城售后详情里的「资金到账时间」取自同一列，必须与流水一致，不能退回成退款单完成时刻。
        List<MallAfterSaleFundingTimeVO> fundingTimes =
                mallAfterSaleFundingSourceDao.allocationSuccessTimes(fixture.orderRefundId());
        assertThat(fundingTimes).singleElement().satisfies(row -> {
            assertThat(row.getAllocationId()).isEqualTo(fixture.allocationId());
            assertThat(row.getSucceededAt())
                    .as("商城展示的资金到账时间必须等于实际返还时刻")
                    .isEqualTo(occurredAt);
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("钱包账户不可用时，分摊失败与幂等结果仍能提交，不留余额返还流水")
    void failedBalanceAllocationPersistsItsFailureOutsideTheTestTransaction() {
        // 本用例会提交带唯一前缀的夹具，只能在授权的隔离 PostgreSQL 中执行。
        BalanceAllocationFixture fixture = completedRefundWithBalanceAllocation();
        jdbc.update("UPDATE xsy_customer_balance_account SET deleted = TRUE "
                + "WHERE settlement_customer_id = (SELECT settlement_customer_id "
                + "FROM xsy_refund_funding_allocation WHERE id = ?)", fixture.allocationId());
        RefundFundingExecuteForm form = new RefundFundingExecuteForm();
        form.setRefundId(fixture.orderRefundId());
        String key = prefix + ":failed-allocation:" + UUID.randomUUID();

        var result = refundFundingAllocationService.execute(form, key);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT status FROM xsy_refund_funding_allocation WHERE id = ?",
                String.class, fixture.allocationId())).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM xsy_idempotency_record "
                + "WHERE idempotency_key = ? AND result_data IS NOT NULL)", Boolean.class, key)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_customer_balance_movement "
                + "WHERE source_type = 'REFUND_FUNDING_ALLOCATION' AND source_id = ?", Integer.class,
                fixture.allocationId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_finance_payment "
                + "WHERE source_type = 'ORDER_REFUND' AND source_id = ? AND entry_type = 'NORMAL'",
                Integer.class, fixture.orderRefundId())).isZero();
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private record BalanceAllocationFixture(Long orderRefundId, Long allocationId) {
    }

    /**
     * 一张「已完成的退款单 + 一条待执行的余额分摊行 + 一个结算主体钱包」的完整前置。
     *
     * <p>订单资金来源按真实形态补齐（意图 SUCCEEDED + 交易 SUCCEEDED + 余额消费流水），
     * 因为分摊执行入口会锁订单资金，且余额分摊的来源 id 就是那笔消费流水。
     */
    private BalanceAllocationFixture completedRefundWithBalanceAllocation() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("alloc-time");
        Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
        String orderNo = jdbc.queryForObject("SELECT order_no FROM xsy_sales_order WHERE id = ?", String.class,
                orderId);
        BigDecimal refundAmount = jdbc.queryForObject(
                "SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?", BigDecimal.class, orderId);
        Long settlementCustomerId = jdbc.queryForObject(
                "SELECT settlement_customer_id FROM xsy_sales_order WHERE id = ?", Long.class, orderId);
        String settlementCustomerName = jdbc.queryForObject(
                "SELECT settlement_customer_name_snapshot FROM xsy_sales_order WHERE id = ?", String.class, orderId);

        Long accountId = jdbc.queryForObject("""
                INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot,
                    created_by, updated_by)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, settlementCustomerId, settlementCustomerName, operator(), operator());

        // 余额支付意图：ck_payment_intent_method_provider 要求 BALANCE 只能配 INTERNAL_BALANCE 且来源为销售订单。
        Long intentId = jdbc.queryForObject("""
                INSERT INTO xsy_payment_intent
                    (intent_no, customer_id, customer_name_snapshot, source_type, source_id, source_no_snapshot,
                     amount, method, provider, status, succeeded_at, created_by, updated_by)
                VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, CAST(? AS numeric), 'BALANCE', 'INTERNAL_BALANCE', 'SUCCEEDED',
                    CURRENT_TIMESTAMP, ?, ?)
                RETURNING id
                """, Long.class, prefix + "-BI-" + UUID.randomUUID().toString().substring(0, 8), customerId,
                settlementCustomerName, orderId, orderNo, refundAmount, operator(), operator());

        Long transactionId = jdbc.queryForObject("""
                INSERT INTO xsy_payment_transaction
                    (transaction_no, intent_id, provider, provider_transaction_no, amount, provider_amount, status,
                     paid_at, created_by)
                VALUES (?, ?, 'INTERNAL_BALANCE', ?, CAST(? AS numeric), CAST(? AS numeric), 'SUCCEEDED',
                    CURRENT_TIMESTAMP, ?)
                RETURNING id
                """, Long.class, prefix + "-BT-" + UUID.randomUUID().toString().substring(0, 8), intentId,
                prefix + "-BPTN-" + UUID.randomUUID().toString().substring(0, 8), refundAmount, refundAmount,
                operator());

        // 余额消费流水：分摊的 source_id 就是它，财务侧也靠 source_type = PAYMENT_INTENT 认这腿资金来源。
        Long movementId = jdbc.queryForObject("""
                INSERT INTO xsy_customer_balance_movement
                    (movement_no, account_id, settlement_customer_id, customer_id, type, direction, amount,
                     source_type, source_id, reason, occurred_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, 'CONSUME', 'DEBIT', CAST(? AS numeric), 'PAYMENT_INTENT', ?, '余额支付',
                    CURRENT_TIMESTAMP, ?, ?)
                RETURNING id
                """, Long.class, prefix + "-BM-" + UUID.randomUUID().toString().substring(0, 8), accountId,
                settlementCustomerId, customerId, refundAmount, intentId, operator(), operator());

        // ck_order_refund_completion：COMPLETED 必须带 completed_at。
        // return_id 无外键、只要求全表唯一；本用例 NOT_SUPPORTED，行会真实落库，
        // 因此从退货 id 序列取值，避免假 return_id 与后续真实退货 id 撞唯一索引。
        Long orderRefundId = jdbc.queryForObject("""
                INSERT INTO xsy_order_refund
                    (refund_no, return_id, order_id, customer_id, refund_amount, status, completed_at, created_by)
                VALUES (?, nextval(pg_get_serial_sequence('xsy_order_return', 'id')), ?, ?, CAST(? AS numeric), 'COMPLETED',
                    CURRENT_TIMESTAMP, ?)
                RETURNING id
                """, Long.class, prefix + "-AOR-" + UUID.randomUUID().toString().substring(0, 8), orderId,
                customerId, refundAmount, operator());

        Long allocationId = jdbc.queryForObject("""
                INSERT INTO xsy_refund_funding_allocation
                    (order_refund_id, order_id, customer_id, settlement_customer_id, source_method, source_id,
                     source_principal, allocated_amount, status, source_succeeded_at, sort_order, created_by,
                     updated_by)
                VALUES (?, ?, ?, ?, 'BALANCE', ?, CAST(? AS numeric), CAST(? AS numeric), 'PENDING',
                    CURRENT_TIMESTAMP, 0, ?, ?)
                RETURNING id
                """, Long.class, orderRefundId, orderId, customerId, settlementCustomerId, movementId,
                refundAmount, refundAmount, operator(), operator());

        return new BalanceAllocationFixture(orderRefundId, allocationId);
    }

    private String operator() {
        return "IT-ALLOC-TIME";
    }
}
