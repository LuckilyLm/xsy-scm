package net.lab1024.sa.admin.module.scm.finance;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.finance.domain.form.FinancePaymentAddForm;
import net.lab1024.sa.admin.module.scm.finance.service.FinancePaymentService;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderRefundCompleteForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnAddForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnApproveForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnApproveItemForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnItemForm;
import net.lab1024.sa.admin.module.scm.order.service.OrderRefundService;
import net.lab1024.sa.admin.module.scm.order.service.OrderReturnService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 付款登记的原子性（F1-3B，PG IT，无外层事务）。
 *
 * <p>付款是一条资金事实，幂等 claim 也在同一个事务里，因此任何一段失败都必须「整笔什么都不留」。
 * 两个失败点各有取证价值：
 * <ul>
 *   <li><b>日志阶段失败</b> —— 留下付款而没有 {@code PAY} 日志，等于资金动作没有证据；</li>
 *   <li><b>来源唯一索引冲突</b> —— 这是本命令唯一的「预期内失败」，它必须整笔回滚并给出业务码，
 *       否则就会出现「同一张退款付了两笔钱但第二笔没有日志」这种对账对不上的状态。</li>
 * </ul>
 *
 * <p>用 {@code NOT_SUPPORTED} 让每次调用自开事务：外层测试事务包着看永远像「什么都没发生」，
 * 那不是回滚的证据。因为本类会<b>真实提交</b>付款，所有断言一律相对调用前的基线测量，
 * 不按全表零断言（那会把别的用例已提交的事实算成自己的泄漏）。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("付款登记的事务原子性（F1-3B，PG IT，无外层事务）")
class ScmFinancePaymentRollbackPgIT extends ScmW5PgITBase {

    /** 只有本用例的日志行带这个 remark 标记，触发函数因此不可能影响其它用例。 */
    private static final String MARKER = "F1-3B-LOG-FAILURE-" + UUID.randomUUID();

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

    private FinancePaymentAddForm form(Long counterpartyId, String amount, String remark,
                                       String sourceType, Long sourceId) {
        var form = new FinancePaymentAddForm();
        form.setCounterpartyType(sourceType == null ? "SUPPLIER" : "CUSTOMER");
        form.setCounterpartyId(counterpartyId);
        form.setAmount(amount);
        form.setMethod("BANK_TRANSFER");
        form.setPaidAt(OffsetDateTime.now().minusDays(2));
        form.setSourceType(sourceType);
        form.setSourceId(sourceId);
        form.setExternalReference("RB-" + UUID.randomUUID().toString().substring(0, 8));
        form.setRemark(remark);
        return form;
    }

    private Long supplier() {
        return newSupplier("RB" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1000));
    }

    /** 真链路造一张 COMPLETED 退款，返回 {@code (refundId, customerId, refundAmount)}。 */
    private Object[] completedRefund(String tag) {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku(tag);
        Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
        Long orderItemId = confirmedSalesOrderItemId(orderId);

        var create = new OrderReturnAddForm();
        create.setOrderId(orderId);
        create.setReason("品质问题 " + tag);
        var row = new OrderReturnItemForm();
        row.setOrderItemId(orderItemId);
        row.setRequestedQuantity("2.0000");
        create.setItems(List.of(row));
        var created = returns.create(create, prefix + ":rc:" + tag + UUID.randomUUID());

        var approve = new OrderReturnApproveForm();
        approve.setReturnId(created.getReturnId());
        approve.setVersion(created.getVersion());
        var approvedRow = new OrderReturnApproveItemForm();
        approvedRow.setOrderItemId(orderItemId);
        approvedRow.setApprovedQuantity("2.0000");
        approve.setItems(List.of(approvedRow));
        returns.approve(approve, prefix + ":ra:" + tag + UUID.randomUUID());

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
        refunds.complete(complete, prefix + ":fc:" + tag + UUID.randomUUID());

        BigDecimal amount = jdbc.queryForObject(
                "SELECT refund_amount FROM order_refund WHERE id = ?", BigDecimal.class, refundId);
        return new Object[]{refundId, customerId, amount};
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    // ------------------------------------------------------------------
    // A. 付款已插入、操作日志写入失败 → 整笔回滚
    // ------------------------------------------------------------------

    @Test
    @DisplayName("日志阶段失败：付款单、日志、幂等记录三者零残留")
    void operationLogFailureRollsBackTheWholePayment() {
        Long supplierId = supplier();
        String idempotencyKey = prefix + ":log-failure:" + UUID.randomUUID();

        jdbc.execute("CREATE OR REPLACE FUNCTION scm_f13b_break_payment_log() RETURNS trigger AS "
                + "$BODY$ BEGIN IF NEW.after_data->>'remark' = '" + MARKER + "' THEN"
                + " RAISE EXCEPTION 'injected finance_operation_log failure'; END IF;"
                + " RETURN NEW; END $BODY$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER trg_scm_f13b_break_payment_log"
                + " BEFORE INSERT ON finance_operation_log"
                + " FOR EACH ROW EXECUTE FUNCTION scm_f13b_break_payment_log()");
        try {
            assertThatThrownBy(() -> financePaymentService.add(
                    form(supplierId, "88.0000", MARKER, null, null), idempotencyKey))
                    .isInstanceOf(Throwable.class);

            assertThat(count("SELECT count(*) FROM finance_payment WHERE counterparty_id = ?", supplierId))
                    .as("付款单不得留下").isZero();
            assertThat(count("SELECT count(*) FROM finance_operation_log WHERE business_type = 'PAYMENT'"
                    + " AND after_data->>'remark' = ?", MARKER))
                    .as("日志本身也没有").isZero();
            assertThat(count("SELECT count(*) FROM idempotency_record WHERE idempotency_key = ?", idempotencyKey))
                    .as("claim 随事务回滚，否则同一 key 的重放会读到空结果").isZero();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS trg_scm_f13b_break_payment_log ON finance_operation_log");
            jdbc.execute("DROP FUNCTION IF EXISTS scm_f13b_break_payment_log()");
        }

        // 触发器已摘除，同一 key 现在能正常登记一次（也证明上一条的零残留不是假象）
        Long paymentId = financePaymentService.add(
                form(supplierId, "88.0000", MARKER, null, null), idempotencyKey).getPaymentId();
        assertThat(paymentId).isNotNull();
        assertThat(count("SELECT count(*) FROM finance_operation_log WHERE business_type = 'PAYMENT'"
                + " AND business_id = ?", paymentId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // B. 同一退款二次付款：来源唯一索引冲突 → 原事实保持、失败侧零残留
    // ------------------------------------------------------------------

    @Test
    @DisplayName("退款来源唯一冲突：原付款保持、无第二笔、无第二份日志、失败请求的 claim 不留在库里")
    void duplicateRefundSourceRollsBackCleanly() {
        Object[] refund = completedRefund("B");
        Long refundId = (Long) refund[0];
        Long customerId = (Long) refund[1];
        String amount = ((BigDecimal) refund[2]).toPlainString();
        String firstKey = prefix + ":paid:" + UUID.randomUUID();
        String secondKey = prefix + ":again:" + UUID.randomUUID();

        Long firstId = financePaymentService.add(form(customerId, amount, "首次付款", "ORDER_REFUND", refundId),
                firstKey).getPaymentId();

        int paymentsBefore = count("SELECT count(*) FROM finance_payment");
        int logsBefore = count("SELECT count(*) FROM finance_operation_log WHERE business_type = 'PAYMENT'");

        assertThatThrownBy(() -> financePaymentService.add(
                form(customerId, amount, "重复付款", "ORDER_REFUND", refundId), secondKey))
                .isInstanceOf(net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException.class);

        assertThat(count("SELECT count(*) FROM finance_payment")).isEqualTo(paymentsBefore);
        assertThat(count("SELECT count(*) FROM finance_payment WHERE source_id = ?", refundId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM finance_operation_log WHERE business_type = 'PAYMENT'"))
                .as("失败的那一次一条日志都不留").isEqualTo(logsBefore);
        assertThat(count("SELECT count(*) FROM idempotency_record WHERE idempotency_key = ?", secondKey))
                .as("既有框架的真实语义：claim 与业务写同事务，整笔回滚后即 0 行，"
                        + "因此同一把失败 key 可以重放而不是留下一个空结果占位").isZero();
        assertThat(count("SELECT count(*) FROM finance_payment WHERE id = ?", firstId))
                .as("原付款一字未动，付款是 append-only 事实").isEqualTo(1);
    }
}
