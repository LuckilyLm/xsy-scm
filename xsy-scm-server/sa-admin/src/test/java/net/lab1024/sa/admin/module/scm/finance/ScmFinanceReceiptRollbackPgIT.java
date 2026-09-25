package net.lab1024.sa.admin.module.scm.finance;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.finance.domain.form.FinanceReceiptAddForm;
import net.lab1024.sa.admin.module.scm.finance.service.FinanceReceiptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 收款登记的原子性（F1-3A，PG IT，无外层事务）。
 *
 * <p>收款是一条资金事实，幂等 claim 也在同一个事务里。因此任何一段失败都必须「整笔什么都不留」：
 * 留下收款单而没有日志 = 资金动作没有证据；留下 claim 而没有结果 = 同一把 key 的后续重放
 * 只能读到空结果（{@code OrderIdempotencyService} 对这种状态就是直接抛错）。
 *
 * <p>用 {@code NOT_SUPPORTED} 让每次调用自开事务：外层测试事务包着看永远像「什么都没发生」，
 * 那不是回滚的证据。两个用例分别把失败放在收款插入之后（日志阶段）与插入之时（单号撞唯一索引）。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("收款登记的事务原子性（F1-3A，PG IT，无外层事务）")
class ScmFinanceReceiptRollbackPgIT extends ScmW5PgITBase {

    /** 只有本用例的日志行带这个 remark 标记，触发函数因此不可能影响其它用例。 */
    private static final String MARKER = "F1-3A-LOG-FAILURE-" + UUID.randomUUID();

    @Autowired
    private FinanceReceiptService financeReceiptService;

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
    }

    private FinanceReceiptAddForm form(Long customerId, String amount, String remark) {
        var form = new FinanceReceiptAddForm();
        form.setCustomerId(customerId);
        form.setAmount(amount);
        form.setMethod("CASH");
        form.setReceivedAt(java.time.OffsetDateTime.now().minusDays(1));
        form.setExternalReference("RB-" + UUID.randomUUID().toString().substring(0, 8));
        form.setRemark(remark);
        return form;
    }

    private Long customer() {
        Long customerId = newCustomer();
        return customerId;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    // ------------------------------------------------------------------
    // 1. 收款已插入、操作日志写入失败 → 整笔回滚
    // ------------------------------------------------------------------

    @Test
    @DisplayName("日志阶段失败：收款单、日志、幂等记录三者零残留")
    void operationLogFailureRollsBackTheWholeReceipt() {
        Long customerId = customer();
        String idempotencyKey = prefix + ":log-failure:" + UUID.randomUUID();

        // 可控的「后段失败」：只在带本次标记的日志行上抛异常，真实链路照常。
        jdbc.execute("CREATE OR REPLACE FUNCTION scm_f13a_break_receipt_log() RETURNS trigger AS "
                + "$BODY$ BEGIN IF NEW.after_data->>'remark' = '" + MARKER + "' THEN"
                + " RAISE EXCEPTION 'injected finance_operation_log failure'; END IF;"
                + " RETURN NEW; END $BODY$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER trg_scm_f13a_break_receipt_log"
                + " BEFORE INSERT ON finance_operation_log"
                + " FOR EACH ROW EXECUTE FUNCTION scm_f13a_break_receipt_log()");
        try {
            assertThatThrownBy(() -> financeReceiptService.add(form(customerId, "66.0000", MARKER), idempotencyKey))
                    .isInstanceOf(Throwable.class);

            assertThat(count("SELECT count(*) FROM finance_receipt WHERE customer_id = ?", customerId))
                    .as("收款单不得留下").isZero();
            assertThat(count("SELECT count(*) FROM finance_operation_log WHERE business_type = 'RECEIPT'"
                    + " AND after_data->>'remark' = ?", MARKER))
                    .as("日志本身也没有").isZero();
            assertThat(count("SELECT count(*) FROM idempotency_record WHERE idempotency_key = ?", idempotencyKey))
                    .as("claim 随事务回滚，否则同一 key 的重放会读到空结果").isZero();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS trg_scm_f13a_break_receipt_log ON finance_operation_log");
            jdbc.execute("DROP FUNCTION IF EXISTS scm_f13a_break_receipt_log()");
        }

        // 触发器已摘除，同一 key 现在能正常登记一次（也证明上一条的零残留不是假象）
        Long receiptId = financeReceiptService.add(form(customerId, "66.0000", MARKER), idempotencyKey)
                .getReceiptId();
        assertThat(receiptId).isNotNull();
        assertThat(count("SELECT count(*) FROM finance_operation_log WHERE business_type = 'RECEIPT'"
                + " AND business_id = ?", receiptId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 2. 收款插入本身失败（单号撞唯一索引）→ 日志与 claim 都不留
    // ------------------------------------------------------------------

    @Test
    @DisplayName("插入阶段失败（receipt_no 撞 uk_finance_receipt_no）：日志与幂等记录零残留")
    void receiptNoCollisionRollsBackLogAndClaim() {
        Long customerId = customer();
        String idempotencyKey = prefix + ":no-collision:" + UUID.randomUUID();

        // 预先占掉服务侧下一次将要使用的单号：序列 nextval 不回滚，因此这次取号是确定的
        long next = jdbc.queryForObject("SELECT nextval('finance_receipt_no_seq')", Long.class);
        String taken = "RC" + java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", next + 1);
        jdbc.update("INSERT INTO finance_receipt (receipt_no, customer_id, customer_name_snapshot,"
                        + " amount, method, received_at, entry_type, created_at, updated_at, created_by)"
                        + " VALUES (?, ?, '占位客户', 1.0000, 'CASH', now(), 'NORMAL', now(), now(), '2:1')",
                taken, customerId);

        // 基线：本类是 NOT_SUPPORTED（真提交），前一个用例留下的收款与日志是既有事实，
        // 因此「零残留」只能相对本次调用之前测量，按全局零断言会把别人的证据算成自己的泄漏。
        int receiptsBefore = count("SELECT count(*) FROM finance_receipt");
        int receiveLogsBefore = count("SELECT count(*) FROM finance_operation_log"
                + " WHERE business_type = 'RECEIPT'");

        try {
            assertThatThrownBy(() -> financeReceiptService.add(form(customerId, "33.0000", "撞号用例"),
                    idempotencyKey))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(count("SELECT count(*) FROM finance_receipt"))
                    .as("基线已含夹具那一行，服务侧什么都没写")
                    .isEqualTo(receiptsBefore);
            assertThat(count("SELECT count(*) FROM finance_receipt WHERE customer_id = ?"
                    + " AND receipt_no <> ?", customerId, taken))
                    .as("除夹具那一行外没有新收款单").isZero();
            assertThat(count("SELECT count(*) FROM finance_operation_log WHERE business_type = 'RECEIPT'"))
                    .as("日志与收款单同事务，一起回滚").isEqualTo(receiveLogsBefore);
            assertThat(count("SELECT count(*) FROM idempotency_record WHERE idempotency_key = ?", idempotencyKey))
                    .as("幂等 claim 也随事务回滚").isZero();
        } finally {
            jdbc.update("DELETE FROM finance_receipt WHERE receipt_no = ?", taken);
        }
    }
}
