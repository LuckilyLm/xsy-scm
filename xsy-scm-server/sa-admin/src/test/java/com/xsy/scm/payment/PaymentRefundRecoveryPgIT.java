package com.xsy.scm.payment;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.payment.dao.PaymentRefundDao;
import com.xsy.scm.payment.domain.entity.PaymentRefundEntity;
import com.xsy.scm.payment.domain.vo.PaymentRefundRecoveryLogVO;
import com.xsy.scm.payment.provider.ScmPaymentProvider;
import com.xsy.scm.payment.service.PaymentRefundService;
import com.xsy.scm.payment.service.RefundFundingCompletionService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 退款恢复链的持久化语义（V125，PG IT）。
 *
 * <p><b>为什么要这一组用例</b>：{@code db0374ab} 把退款从「同步调渠道」改成「持久化退款意图 → 定时查单 →
 * 失败恢复 → 资金完成自动入账」，这条链的正确性全压在几个<b>只有真实数据库才能验证</b>的语义上：
 * 租约的单飞性（多实例不重复派发）、租约到期后的可恢复性（进程重启）、候选行的到期与入账筛选、
 * 恢复审计的追加与不可改、以及渠道实退与申请额不一致时「保留渠道事实、不生成财务付款」。
 * 这些用 Mockito 都测不出来 —— mock 掉的就是被验证的那条 SQL。
 *
 * <p><b>为什么不测 {@code PaymentRefundRecoveryService.recover()} 本身</b>：它是
 * {@code Propagation.NEVER}，{@code PaymentRefundRecoveryStore.claim/finish} 是 {@code REQUIRES_NEW}。
 * 在外层事务里调用会直接抛「Existing transaction found for transaction marked with propagation 'never'」；
 * 而 {@code REQUIRES_NEW} 的新连接看不到本用例尚未提交的夹具行，claim 只会拿到 null。
 * 因此本类钉住的是它下面那层 DAO 语义（也是 V125 新增的全部 SQL），
 * 与 {@code MockPaymentLedgerRollbackPgIT} 用 {@code NOT_SUPPORTED} 处理同一类问题的做法一致。
 *
 * <p><b>造数</b>：订单与客户走真实领域服务（{@code ScmW5PgITBase}），
 * 支付意图 / 交易 / 售后退款单 / 渠道退款行按表结构直接插 —— 它们不是本用例的被测对象，
 * 只是「恢复链能跑起来」的前置事实。
 */
@DisplayName("退款恢复链的持久化语义（V125，PG IT）")
class PaymentRefundRecoveryPgIT extends ScmW5PgITBase {

    /**
     * 与 {@code PaymentRefundRecoveryJob} 一致：候选查询按 {@code (next_recovery_at, id)} 升序取前 N 条。
     * 用例里取一个大 N，避免开发库里别人留下的到期行把本用例的行挤出窗口。
     */
    private static final int CANDIDATE_WINDOW = 2000;

    @Autowired
    private PaymentRefundDao paymentRefundDao;

    @Autowired
    private PaymentRefundService paymentRefundService;

    @Autowired
    private RefundFundingCompletionService refundFundingCompletionService;

    // ------------------------------------------------------------------
    // 租约：多实例不重复派发 / 进程重启后仍可恢复
    // ------------------------------------------------------------------

    @Test
    @DisplayName("租约未过期时第二次取租约必须失败：多实例不会重复向渠道派发")
    void leaseKeepsASingleDispatcher() {
        RefundFixture fixture = recoveryFixture("LEASE", "30.0000", "PROCESSING");

        assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), false)).isEqualTo(1);
        assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), false))
                .as("同一租约窗口内第二次取租约必须失败，否则两个实例会各自向渠道发起一次退款")
                .isZero();
        assertThat(recoveryAttempts(fixture.refundId()))
                .as("失败的取租约不能计入重试次数")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("租约到期后可再次取租约：进程被中断的退款能继续恢复")
    void expiredLeaseIsRecoverableAfterRestart() {
        RefundFixture fixture = recoveryFixture("RESTART", "30.0000", "PROCESSING");
        assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), false)).isEqualTo(1);

        // 进程在持有租约时被杀：租约不会自己释放，只能等它过期。
        jdbc.update("UPDATE xsy_payment_refund SET recovery_lease_until = CURRENT_TIMESTAMP - INTERVAL '1 minute' "
                + "WHERE id = ?", fixture.refundId());

        assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), false))
                .as("租约过期即视为上一次恢复已中断，必须允许接手")
                .isEqualTo(1);
        assertThat(recoveryAttempts(fixture.refundId())).isEqualTo(2);
    }

    @Test
    @DisplayName("人工恢复入口可以绕过下次恢复时间，但不能绕过租约")
    void forceBypassesBackoffButNotTheLease() {
        RefundFixture fixture = recoveryFixture("FORCE", "30.0000", "PROCESSING");
        pushNextRecovery(fixture.refundId(), 1);

        assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), false))
                .as("退避窗口内定时任务不得抢跑")
                .isZero();
        assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), true))
                .as("人工恢复是显式动作，允许无视退避窗口")
                .isEqualTo(1);
        assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), true))
                .as("但租约仍然生效：人工入口也不能把同一笔退款并发派发两次")
                .isZero();
    }

    // ------------------------------------------------------------------
    // 候选筛选：定时任务该捞谁
    // ------------------------------------------------------------------

    @Test
    @DisplayName("候选只包含到期且未租用的行；已入账的成功退款不再重复恢复")
    void candidatesOnlyPickDueUnleasedAndUnsettledRefunds() {
        RefundFixture due = recoveryFixture("CAND-DUE", "30.0000", "PROCESSING");

        RefundFixture leased = recoveryFixture("CAND-LEASED", "30.0000", "PROCESSING");
        assertThat(paymentRefundDao.claimRecovery(leased.refundId(), false)).isEqualTo(1);

        RefundFixture backedOff = recoveryFixture("CAND-BACKOFF", "30.0000", "PROCESSING");
        pushNextRecovery(backedOff.refundId(), 60);

        // 渠道已退成功、但本地还没登记财务付款：这正是「回调丢失」的现场，必须能被捞回来。
        RefundFixture settledByChannel = recoveryFixture("CAND-UNPAID", "30.0000", "SUCCEEDED");

        // 已经登记过财务付款的成功退款：再捞一次只会重复查单，必须排除。
        RefundFixture alreadyBooked = recoveryFixture("CAND-BOOKED", "30.0000", "SUCCEEDED");
        insertFinancePayment(alreadyBooked);

        List<Long> candidates = paymentRefundDao.recoveryCandidates(CANDIDATE_WINDOW);

        assertThat(candidates)
                .as("到期的处理中退款要入选")
                .contains(due.refundId());
        assertThat(candidates)
                .as("渠道已退成功但缺财务付款的退款必须入选，否则这笔账永远补不回来")
                .contains(settledByChannel.refundId());
        assertThat(candidates)
                .as("租约未过期的行属于「别人正在处理」，不得被抢")
                .doesNotContain(leased.refundId());
        assertThat(candidates)
                .as("退避窗口内的行不得被抢跑")
                .doesNotContain(backedOff.refundId());
        assertThat(candidates)
                .as("财务付款已登记的成功退款不该再进恢复队列")
                .doesNotContain(alreadyBooked.refundId());
    }

    // ------------------------------------------------------------------
    // 恢复结果落库：退避、状态、审计
    // ------------------------------------------------------------------

    @Test
    @DisplayName("结束恢复要释放租约、记录结果，并把下次恢复时间推后")
    void finishReleasesLeaseAndBacksOff() {
        RefundFixture fixture = recoveryFixture("FINISH", "30.0000", "PROCESSING");
        assertThat(paymentRefundDao.claimRecovery(fixture.refundId(), false)).isEqualTo(1);

        PaymentRefundEntity claimed = paymentRefundDao.selectById(fixture.refundId());
        OffsetDateTime txNow = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class);
        assertThat(paymentRefundDao.finishRecovery(fixture.refundId(), "FAILED", "渠道查单未完成", 60,
                claimed.getRecoveryAttempts())).isEqualTo(1);

        evictMybatisCache();
        PaymentRefundEntity after = paymentRefundDao.selectById(fixture.refundId());
        assertThat(after.getRecoveryLeaseUntil()).as("租约必须释放，否则这一笔再也恢复不了").isNull();
        assertThat(after.getRecoveryStatus()).isEqualTo("FAILED");
        assertThat(after.getRecoveryMessage()).isEqualTo("渠道查单未完成");
        assertThat(after.getNextRecoveryAt())
                .as("下一次恢复时间必须被推后（退避），否则会变成忙等")
                .isAfter(txNow.plusSeconds(59));
    }

    @Test
    @DisplayName("恢复审计只追加、按时间倒序，且数据库层面拒绝改写与删除")
    void recoveryAuditIsAppendOnly() {
        RefundFixture fixture = recoveryFixture("AUDIT", "30.0000", "PROCESSING");

        assertThat(paymentRefundDao.appendRecoveryLog(fixture.refundId(), "IT-RECOVERY", "第一次查单", "STARTED",
                null, "开始渠道查单")).isEqualTo(1);
        assertThat(paymentRefundDao.appendRecoveryLog(fixture.refundId(), "IT-RECOVERY", "第二次查单",
                "AMOUNT_MISMATCH", new BigDecimal("29.0000"), "渠道实退与申请金额不一致")).isEqualTo(1);

        List<PaymentRefundRecoveryLogVO> logs = paymentRefundDao.recoveryLogs(fixture.refundId());
        assertThat(logs).hasSize(2);
        assertThat(logs.getFirst().getResult()).as("最新一条排在最前").isEqualTo("AMOUNT_MISMATCH");
        assertThat(logs.getFirst().getProviderAmount()).isEqualByComparingTo("29.0000");
        assertThat(logs.getLast().getResult()).isEqualTo("STARTED");
        assertThat(logs.getFirst().getOperator()).isEqualTo("IT-RECOVERY");

        expectSqlFailure("UPDATE xsy_payment_refund_recovery_log SET result = 'SUCCEEDED' WHERE refund_id = ?",
                fixture.refundId());
        expectSqlFailure("DELETE FROM xsy_payment_refund_recovery_log WHERE refund_id = ?", fixture.refundId());
    }

    // ------------------------------------------------------------------
    // 渠道结果落账：金额差异与重复回调
    // ------------------------------------------------------------------

    @Test
    @DisplayName("渠道实退与申请额不一致：保留渠道事实，但不生成财务付款")
    void amountMismatchKeepsChannelFactWithoutFinancePayment() {
        RefundFixture fixture = recoveryFixture("MISMATCH", "30.0000", "PROCESSING");

        paymentRefundService.applyOutcome(fixture.refundId(), ScmPaymentProvider.Outcome.SUCCEEDED,
                "MOCK-REFUND-" + fixture.refundId(), new BigDecimal("29.0000"), null, null, "IT-RECOVERY");

        evictMybatisCache();
        PaymentRefundEntity after = paymentRefundDao.selectById(fixture.refundId());
        assertThat(after.getStatus()).as("渠道确实退了钱，本地不能因为金额差异把它判成失败").isEqualTo("SUCCEEDED");
        assertThat(after.getProviderAmount()).isEqualByComparingTo("29.0000");
        assertThat(after.getAmount()).as("申请额保持不变：差异留给人工对账，不自动改写").isEqualByComparingTo("30.0000");
        assertThat(financePaymentCount(fixture.orderRefundId()))
                .as("实退与应退不一致时不得登记财务付款，否则财务账上会出现一个错误的实退额")
                .isZero();
    }

    @Test
    @DisplayName("渠道明确失败：退款转 FAILED 并留下失败码，不生成财务付款")
    void explicitChannelFailureMarksRefundFailed() {
        RefundFixture fixture = recoveryFixture("CHANNEL-FAILED", "30.0000", "PROCESSING");

        paymentRefundService.applyOutcome(fixture.refundId(), ScmPaymentProvider.Outcome.FAILED, null, null,
                "MOCK_REFUND_REJECTED", "模拟渠道拒绝退款", "IT-RECOVERY");

        evictMybatisCache();
        PaymentRefundEntity after = paymentRefundDao.selectById(fixture.refundId());
        assertThat(after.getStatus()).isEqualTo("FAILED");
        assertThat(after.getFailureCode()).isEqualTo("MOCK_REFUND_REJECTED");
        assertThat(after.getProviderAmount()).as("失败态不得留下渠道实退额").isNull();
        assertThat(financePaymentCount(fixture.orderRefundId())).isZero();
    }

    @Test
    @DisplayName("重复回调同一结果：不抛异常、不改写已确认的退款时间")
    void repeatedIdenticalOutcomeIsIdempotent() {
        RefundFixture fixture = recoveryFixture("REPEAT", "30.0000", "PROCESSING");
        String providerRefundNo = "MOCK-REFUND-" + fixture.refundId();

        paymentRefundService.applyOutcome(fixture.refundId(), ScmPaymentProvider.Outcome.SUCCEEDED, providerRefundNo,
                new BigDecimal("29.0000"), null, null, "IT-RECOVERY");
        evictMybatisCache();
        OffsetDateTime firstRefundedAt = paymentRefundDao.selectById(fixture.refundId()).getRefundedAt();

        paymentRefundService.applyOutcome(fixture.refundId(), ScmPaymentProvider.Outcome.SUCCEEDED, providerRefundNo,
                new BigDecimal("29.0000"), null, null, "IT-RECOVERY");

        evictMybatisCache();
        PaymentRefundEntity after = paymentRefundDao.selectById(fixture.refundId());
        assertThat(after.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(after.getRefundedAt())
                .as("已成功行不该被重复回调改写：退款时点一旦确认就不能漂移")
                .isEqualTo(firstRefundedAt);
        assertThat(after.getVersion()).as("重复回调不应产生一次状态变更").isEqualTo(1);
    }

    @Test
    @DisplayName("已确认成功的退款不允许改掉渠道退款号：那等于把凭据指向另一笔渠道资金")
    void changingProviderReferenceOnSettledRefundIsRejected() {
        RefundFixture fixture = recoveryFixture("REBIND", "30.0000", "PROCESSING");

        paymentRefundService.applyOutcome(fixture.refundId(), ScmPaymentProvider.Outcome.SUCCEEDED,
                "MOCK-REFUND-" + fixture.refundId(), new BigDecimal("29.0000"), null, null, "IT-RECOVERY");

        assertThatThrownBy(() -> paymentRefundService.applyOutcome(fixture.refundId(),
                ScmPaymentProvider.Outcome.SUCCEEDED, "MOCK-REFUND-OTHER", new BigDecimal("29.0000"), null, null,
                "IT-RECOVERY"))
                .as("渠道退款号是幂等锚点，改掉它会让同一笔本地退款对上两笔渠道资金")
                .isInstanceOf(ScmBusinessException.class);
    }

    // ------------------------------------------------------------------
    // 资金分摊完成 → 唯一财务付款
    // ------------------------------------------------------------------

    @Test
    @DisplayName("全部分摊成功后自动登记唯一一条退款财务付款，重复推进不产生第二条")
    void fundingCompletionRegistersExactlyOneFinancePayment() {
        RefundFixture fixture = recoveryFixture("ALLOCATION", "30.0000", "SUCCEEDED");
        Long allocationId = insertSucceededOnlineAllocation(fixture, "30.0000");

        Long paymentId = refundFundingCompletionService.complete(fixture.orderRefundId(), "IT-RECOVERY");
        assertThat(paymentId).as("分摊全部成功且合计等于应退额时必须自动入账，不再依赖人工点执行").isNotNull();
        assertThat(financePaymentCount(fixture.orderRefundId())).isEqualTo(1);
        assertThat(financePaymentMethod(fixture.orderRefundId()))
                .as("按原资金来源退回，方法必须是 ORIGINAL_FUNDING 而不是 ONLINE_PAYMENT")
                .isEqualTo("ORIGINAL_FUNDING");
        assertThat(financePaymentAmount(fixture.orderRefundId())).isEqualByComparingTo("30.0000");

        // 重复推进（回调重放、恢复任务再次扫到）不得产生第二条付款事实。
        refundFundingCompletionService.complete(fixture.orderRefundId(), "IT-RECOVERY");
        assertThat(financePaymentCount(fixture.orderRefundId())).isEqualTo(1);

        evictMybatisCache();
        assertThat(allocationStatus(allocationId)).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("分摊合计与应退额不一致时拒绝入账，不留下一个金额错误的付款事实")
    void fundingCompletionRejectsAmountMismatch() {
        RefundFixture fixture = recoveryFixture("ALLOC-MISMATCH", "30.0000", "SUCCEEDED");
        insertSucceededOnlineAllocation(fixture, "29.0000");

        assertThatThrownBy(() -> refundFundingCompletionService.complete(fixture.orderRefundId(), "IT-RECOVERY"))
                .as("实退合计与应退额不等时必须拒绝登记财务付款，而不是按其中一个数落账")
                .isInstanceOf(ScmBusinessException.class);
        assertThat(financePaymentCount(fixture.orderRefundId())).isZero();
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /**
     * 一笔可被恢复链处理的退款及其上下游事实。
     */
    private record RefundFixture(Long customerId, Long orderId, Long orderRefundId, Long transactionId,
                                 Long refundId) {
    }

    /**
     * 造一笔「已确认订单 + 成功渠道支付 + 已完成售后退款 + 渠道退款行」的完整前置。
     *
     * @param refundAmount 售后退款单的应退额，同时作为渠道退款行的申请额
     * @param status       渠道退款行的初始状态
     */
    private RefundFixture recoveryFixture(String suffix, String refundAmount, String status) {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku(suffix);
        Long orderId = confirmedSalesOrder(customerId, skuId, "100.0000", "100.0000");
        String orderNo = jdbc.queryForObject("SELECT order_no FROM xsy_sales_order WHERE id = ?", String.class,
                orderId);

        // 支付意图与交易：恢复链只读它们来定位渠道交易号，不校验其金额口径。
        Long intentId = jdbc.queryForObject("""
                INSERT INTO xsy_payment_intent
                    (intent_no, customer_id, customer_name_snapshot, source_type, source_id, source_no_snapshot,
                     amount, method, provider, status, succeeded_at)
                VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, 100.0000, 'ONLINE', 'MOCK', 'SUCCEEDED', CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, prefix + "-INT-" + suffix, customerId, prefix + "客户", orderId, orderNo);

        // ck_payment_transaction_paid_pairing：SUCCEEDED 必须带 paid_at。
        Long transactionId = jdbc.queryForObject("""
                INSERT INTO xsy_payment_transaction
                    (transaction_no, intent_id, provider, provider_transaction_no, amount, provider_amount, status,
                     paid_at, created_by)
                VALUES (?, ?, 'MOCK', ?, 100.0000, 100.0000, 'SUCCEEDED', CURRENT_TIMESTAMP, ?)
                RETURNING id
                """, Long.class, prefix + "-TXN-" + suffix, intentId, prefix + "-PTN-" + suffix, operator());

        // ck_order_refund_completion：COMPLETED 必须带 completed_at。
        // return_id 只被 uk_order_refund_return_active 约束（该表对本用例没有外键），
        // 但它是全表唯一：同一个用例造多笔退款时必须各不相同，否则第二笔直接撞唯一索引。
        // 与 id 共用序列既能保证唯一，也不会把值挤出 bigint 的常规区间。
        Long orderRefundId = jdbc.queryForObject("""
                INSERT INTO xsy_order_refund
                    (refund_no, return_id, order_id, customer_id, refund_amount, status, completed_at, created_by)
                VALUES (?, nextval('xsy_v2.order_refund_id_seq'), ?, ?, CAST(? AS numeric), 'COMPLETED',
                    CURRENT_TIMESTAMP, ?)
                RETURNING id
                """, Long.class, prefix + "-ORF-" + suffix, orderId, customerId, refundAmount, operator());

        Long refundId = insertRefund(intentId, transactionId, orderRefundId, refundAmount, status);
        return new RefundFixture(customerId, orderId, orderRefundId, transactionId, refundId);
    }

    /**
     * 渠道退款行。成功态按库上的 CHECK 同时补 {@code provider_amount} 与 {@code refunded_at}。
     */
    private Long insertRefund(Long intentId, Long transactionId, Long orderRefundId, String amount, String status) {
        boolean succeeded = "SUCCEEDED".equals(status);
        return jdbc.queryForObject("""
                INSERT INTO xsy_payment_refund
                    (refund_no, intent_id, transaction_id, provider, amount, source_type, source_id, status,
                     provider_amount, refunded_at, mock_scenario, reason, created_by, updated_by)
                VALUES (?, ?, ?, 'MOCK', CAST(? AS numeric), 'ORDER_REFUND', ?, ?, ?, ?, 'SUCCESS', ?, ?, ?)
                RETURNING id
                """, Long.class, prefix + "-PRF-" + UUID.randomUUID().toString().substring(0, 8), intentId,
                transactionId, new BigDecimal(amount), orderRefundId, status,
                succeeded ? new BigDecimal(amount) : null,
                succeeded ? OffsetDateTime.now() : null,
                "退款恢复链 IT", operator(), operator());
    }

    /**
     * 登记一条财务退款付款事实，用于构造「这笔成功退款已经入账、不该再被恢复」的行。
     */
    private void insertFinancePayment(RefundFixture fixture) {
        jdbc.update("""
                INSERT INTO xsy_finance_payment
                    (payment_no, counterparty_type, counterparty_id, counterparty_name_snapshot, amount, method,
                     paid_at, entry_type, source_type, source_id, created_by, updated_by)
                VALUES (?, 'CUSTOMER', ?, ?, 30.0000, 'ORIGINAL_FUNDING', CURRENT_TIMESTAMP, 'NORMAL',
                    'ORDER_REFUND', ?, ?, ?)
                """, prefix + "-FP-" + UUID.randomUUID().toString().substring(0, 8), fixture.customerId(),
                prefix + "客户", fixture.orderRefundId(), operator(), operator());
    }

    /**
     * 造一条已成功的 ONLINE 资金分摊，以及它对应的渠道退款事实。
     *
     * <p>先插 {@code PENDING} 再回填 {@code result_source_id}：成功态要求 {@code result_source_id} 非空，
     * 而它指向的渠道退款行又必须带上分摊 id（{@code source_id}），两边互相引用，只能分两步。
     *
     * <p>这里刻意只造 ONLINE 分摊：BALANCE 分摊的完成判定要读 {@code xsy_customer_balance_movement}
     * 与余额账户，那是余额域自己的造数，与「退款恢复」这条链无关。
     */
    private Long insertSucceededOnlineAllocation(RefundFixture fixture, String allocatedAmount) {
        Long settlementCustomerId = jdbc.queryForObject(
                "SELECT settlement_customer_id FROM xsy_sales_order WHERE id = ?", Long.class, fixture.orderId());

        Long allocationId = jdbc.queryForObject("""
                INSERT INTO xsy_refund_funding_allocation
                    (order_refund_id, order_id, customer_id, settlement_customer_id, source_method, source_id,
                     source_principal, allocated_amount, status, source_succeeded_at, sort_order, created_by,
                     updated_by)
                VALUES (?, ?, ?, ?, 'ONLINE', ?, 100.0000, CAST(? AS numeric), 'PENDING', CURRENT_TIMESTAMP, 0, ?, ?)
                RETURNING id
                """, Long.class, fixture.orderRefundId(), fixture.orderId(), fixture.customerId(),
                settlementCustomerId, fixture.transactionId(), allocatedAmount, operator(), operator());

        Long allocationRefundId = jdbc.queryForObject("""
                INSERT INTO xsy_payment_refund
                    (refund_no, intent_id, transaction_id, provider, amount, source_type, source_id, status,
                     provider_amount, refunded_at, created_by, updated_by)
                SELECT ?, intent_id, transaction_id, provider, CAST(? AS numeric), 'REFUND_FUNDING_ALLOCATION', ?,
                    'SUCCEEDED', CAST(? AS numeric), CURRENT_TIMESTAMP, ?, ?
                FROM xsy_payment_refund WHERE id = ?
                RETURNING id
                """, Long.class, prefix + "-ARF-" + UUID.randomUUID().toString().substring(0, 8), allocatedAmount,
                allocationId, allocatedAmount, operator(), operator(), fixture.refundId());

        jdbc.update("UPDATE xsy_refund_funding_allocation SET status = 'SUCCEEDED', result_source_id = ? "
                + "WHERE id = ?", allocationRefundId, allocationId);
        return allocationId;
    }

    private String allocationStatus(Long allocationId) {
        return jdbc.queryForObject("SELECT status FROM xsy_refund_funding_allocation WHERE id = ?", String.class,
                allocationId);
    }

    private String financePaymentMethod(Long orderRefundId) {
        return jdbc.queryForObject("SELECT method FROM xsy_finance_payment WHERE source_type = 'ORDER_REFUND' "
                + "AND source_id = ? AND deleted = FALSE", String.class, orderRefundId);
    }

    private BigDecimal financePaymentAmount(Long orderRefundId) {
        return jdbc.queryForObject("SELECT amount FROM xsy_finance_payment WHERE source_type = 'ORDER_REFUND' "
                + "AND source_id = ? AND deleted = FALSE", BigDecimal.class, orderRefundId);
    }

    private int recoveryAttempts(Long refundId) {
        evictMybatisCache();
        return paymentRefundDao.selectById(refundId).getRecoveryAttempts();
    }

    private int financePaymentCount(Long orderRefundId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM xsy_finance_payment "
                + "WHERE source_type = 'ORDER_REFUND' AND source_id = ? AND deleted = FALSE", Integer.class,
                orderRefundId);
        return count == null ? 0 : count;
    }

    /** 把下次恢复时间推到 {@code minutesFromNow} 分钟之后，模拟退避窗口。 */
    private void pushNextRecovery(Long refundId, int minutesFromNow) {
        jdbc.update("UPDATE xsy_payment_refund SET next_recovery_at = CURRENT_TIMESTAMP + ("
                + minutesFromNow + " * INTERVAL '1 minute') WHERE id = ?", refundId);
    }

    private String operator() {
        return "IT-RECOVERY";
    }
}
