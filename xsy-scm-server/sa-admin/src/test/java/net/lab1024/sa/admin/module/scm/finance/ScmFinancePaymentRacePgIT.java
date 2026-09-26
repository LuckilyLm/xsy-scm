package net.lab1024.sa.admin.module.scm.finance;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.finance.domain.form.FinancePaymentAddForm;
import net.lab1024.sa.admin.module.scm.finance.service.FinancePaymentService;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderRefundCompleteForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnAddForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnApproveForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnApproveItemForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnItemForm;
import net.lab1024.sa.admin.module.scm.order.service.OrderRefundService;
import net.lab1024.sa.admin.module.scm.order.service.OrderReturnService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 同一退款被**真并发**双付时的收敛（F1-3B，PG IT，无外层事务）。
 *
 * <p>这是第二批 Q26 的核心场景，值得现在就钉而不是等 F1-4：两个<b>不同</b> {@code Idempotency-Key}
 * 同时付同一张 {@code COMPLETED} 退款。幂等键只能防「同一个请求重发」，防不了「两个人各自发起一次」，
 * 而财务上「同一笔退款只付一次钱」必须由库来保证 —— 唯一索引
 * {@code uk_finance_payment_source_active} 在这里是唯一的仲裁点。
 *
 * <p>断言只看最终事实，不写死哪个线程先成功（那是在测调度器）：
 * 恰好一笔付款 + 恰好一条 {@code PAY} 日志 + 恰好一个线程失败，
 * 且失败必须是业务码 41139 —— 不能是裸 {@code DuplicateKeyException}（那会把约束名与 SQLState
 * 泄漏到接口），也不能是死锁或超时（两个线程都只写自己的行，冲突在索引上排队，
 * 后到者等前者提交后重新检查谓词、插入返回 0）。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("同一退款并发双付的收敛（F1-3B，PG IT，无外层事务）")
class ScmFinancePaymentRacePgIT extends ScmW5PgITBase {

    @Autowired
    private FinancePaymentService financePaymentService;

    @Autowired
    private OrderReturnService returns;

    @Autowired
    private OrderRefundService refunds;

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
    }

    private record Refund(Long refundId, Long customerId, BigDecimal refundAmount) {
    }

    /** 真链路建到「退款 COMPLETED」，两条竞争事务的前置全部已提交。 */
    private Refund completedRefund(String tag) {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku(tag);
        Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
        Long orderItemId = confirmedSalesOrderItemId(orderId);

        var create = new OrderReturnAddForm();
        create.setOrderId(orderId);
        create.setReason("品质问题 " + tag);
        var row = new OrderReturnItemForm();
        row.setOrderItemId(orderItemId);
        row.setRequestedQuantity("3.0000");
        create.setItems(List.of(row));
        var created = returns.create(create, key("rc:" + tag));

        var approve = new OrderReturnApproveForm();
        approve.setReturnId(created.getReturnId());
        approve.setVersion(created.getVersion());
        var approvedRow = new OrderReturnApproveItemForm();
        approvedRow.setOrderItemId(orderItemId);
        approvedRow.setApprovedQuantity("3.0000");
        approve.setItems(List.of(approvedRow));
        returns.approve(approve, key("ra:" + tag));

        // 按 order_id 直查退款行，不绕 refunds.query(...).getFirst()：
        // 那个分页读接口按 orderSellerScope 收窄（非超管身份下会拿不到行），
        // 而夹具要的只是「这一单的退款」，直查不依赖读侧范围也不依赖行序。
        Long refundId = jdbc.queryForObject(
                "SELECT id FROM order_refund WHERE order_id = ? AND deleted = FALSE", Long.class, orderId);
        Integer refundVersion = jdbc.queryForObject(
                "SELECT version FROM order_refund WHERE id = ?", Integer.class, refundId);
        var complete = new OrderRefundCompleteForm();
        complete.setRefundId(refundId);
        complete.setVersion(refundVersion);
        // order_refund.external_reference 上有部分唯一索引（uk_order_refund_external_reference_active），
        // 与财务侧刻意「可重复」的同名列正相反；本类是真提交，所以这里的值必须每次唯一，
        // 否则第二遍跑同一个库就会被自己的上一轮挡住（并被 complete 映射成状态错误）。
        complete.setExternalReference("ORDER-SIDE-" + tag + "-" + UUID.randomUUID());
        refunds.complete(complete, key("fc:" + tag));

        return new Refund(refundId, customerId,
                jdbc.queryForObject("SELECT refund_amount FROM order_refund WHERE id = ?",
                        BigDecimal.class, refundId));
    }

    private FinancePaymentAddForm form(Refund refund) {
        var form = new FinancePaymentAddForm();
        form.setCounterpartyType("CUSTOMER");
        form.setCounterpartyId(refund.customerId());
        form.setAmount(refund.refundAmount().toPlainString());
        form.setMethod("BANK_TRANSFER");
        form.setPaidAt(OffsetDateTime.now().minusDays(1));
        form.setSourceType("ORDER_REFUND");
        form.setSourceId(refund.refundId());
        form.setExternalReference("RACE-" + UUID.randomUUID().toString().substring(0, 8));
        form.setRemark("F1-3B 并发用例");
        return form;
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    private void login(String name) {
        var employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName(name);
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

    private void run(String name, Runnable body, AtomicReference<Throwable> failure,
                     CountDownLatch ready, CountDownLatch start, CountDownLatch done) {
        new Thread(() -> {
            login(name);
            try {
                ready.countDown();
                start.await();
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                SmartRequestUtil.remove();
                done.countDown();
            }
        }, name).start();
    }

    @Test
    @DisplayName("两个不同幂等键同时付同一张退款：恰好一笔付款 + 一条日志，失败侧是 41139 而不是裸唯一键异常")
    void concurrentRefundPaymentsConvergeToOneFact() throws Exception {
        Refund refund = completedRefund("RACE1");

        var firstFailure = new AtomicReference<Throwable>();
        var secondFailure = new AtomicReference<Throwable>();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);

        run("pay-thread-a",
                () -> financePaymentService.add(form(refund), key("race-a")),
                firstFailure, ready, start, done);
        run("pay-thread-b",
                () -> financePaymentService.add(form(refund), key("race-b")),
                secondFailure, ready, start, done);

        assertThat(ready.await(30, TimeUnit.SECONDS)).as("两个线程都要到达起跑线").isTrue();
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS))
                .as("两个事务必须在 60 秒内结束（否则疑似死锁或长等待）").isTrue();

        // 必须且只能有一侧失败
        boolean aFailed = firstFailure.get() != null;
        boolean bFailed = secondFailure.get() != null;
        assertThat(aFailed ^ bFailed)
                .as("一侧成功、一侧被来源唯一索引拒绝，不得双双成功或双双失败")
                .isTrue();
        Throwable loser = aFailed ? firstFailure.get() : secondFailure.get();
        assertThat(loser)
                .as("失败必须是可读的业务异常，不能把 DuplicateKeyException / SQLState / 约束名抛到接口")
                .isInstanceOf(ScmBusinessException.class)
                .isNotInstanceOf(DataAccessException.class);
        assertThat(((ScmBusinessException) loser).getErrorCode().getCode())
                .as("FINANCE_PAYMENT_SOURCE_INVALID").isEqualTo(41139);

        // 最终事实：一笔付款、一条日志，两个 claim 各自成对（成功侧完成、失败侧随事务回滚）
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_payment WHERE source_id = ?", Integer.class, refund.refundId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_operation_log l JOIN finance_payment p ON p.id = l.business_id"
                        + " WHERE l.business_type = 'PAYMENT' AND l.operation_type = 'PAY'"
                        + " AND p.source_id = ?", Integer.class, refund.refundId()))
                .as("付款与日志同事务，因此只可能有一条").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_payment WHERE source_id = ? AND entry_type = 'NORMAL'"
                        + " AND amount = ?", Integer.class, refund.refundId(), refund.refundAmount()))
                .as("留下的那一笔金额正是退款应退额").isEqualTo(1);
    }
}
