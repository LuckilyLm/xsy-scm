package net.lab1024.sa.admin.module.scm.finance;

import com.fasterxml.jackson.core.type.TypeReference;
import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.domain.form.FinanceReceiptAddForm;
import net.lab1024.sa.admin.module.scm.finance.domain.vo.FinanceReceiptVO;
import net.lab1024.sa.admin.module.scm.finance.service.FinanceReceiptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 收款登记（Finance R1 F1-3A，PG IT）。
 *
 * <p>这里钉的是「一笔钱就是一个事实」：登记收款只产生 {@code finance_receipt} + 一条
 * {@code RECEIVE} 日志，<b>不</b>自动核销、<b>不</b>改应收、<b>不</b>改客户与订单，
 * 也<b>不</b>要求客户存在任何应收（预收是合法业务，第二批 Q16）。
 *
 * <p>金额形态、方式三值、幂等重放、数据范围与快照冻结各有一条以上断言；
 * 幂等键冲突与越权都必须「什么都不产生」，而不是产生一张孤儿单。
 */
@DisplayName("收款登记（Finance R1 F1-3A，PG IT）")
class ScmFinanceReceiptPgIT extends ScmW5PgITBase {

    private static final OffsetDateTime PAST = OffsetDateTime.of(2026, 3, 5, 9, 30, 0, 0, ZoneOffset.ofHours(8));

    @Autowired
    private FinanceReceiptService financeReceiptService;

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private FinanceReceiptAddForm form(Long customerId, String amount, String method, OffsetDateTime receivedAt) {
        FinanceReceiptAddForm form = new FinanceReceiptAddForm();
        form.setCustomerId(customerId);
        form.setAmount(amount);
        form.setMethod(method);
        form.setReceivedAt(receivedAt);
        form.setExternalReference("BANK-" + UUID.randomUUID().toString().substring(0, 8));
        form.setRemark("F1-3A IT");
        return form;
    }

    private FinanceReceiptVO add(FinanceReceiptAddForm form) {
        return financeReceiptService.add(form, key("add"));
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    private Long customerOwnedBy(Long sellerId) {
        Long customerId = newCustomer();
        jdbc.update("UPDATE customer SET seller_id = ? WHERE id = ?", sellerId, customerId);
        evictMybatisCache();
        return customerId;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private Map<String, Object> receiptRow(Long receiptId) {
        return jdbc.queryForMap("SELECT * FROM finance_receipt WHERE id = ?", receiptId);
    }

    private List<Map<String, Object>> logsOf(Long receiptId) {
        return jdbc.queryForList(
                "SELECT * FROM finance_operation_log WHERE business_type = 'RECEIPT' AND business_id = ?",
                receiptId);
    }

    /**
     * 本用例客户名下的收款日志条数。
     *
     * <p>必须按客户归属收窄：{@link ScmFinanceReceiptRollbackPgIT} 是 {@code NOT_SUPPORTED}
     * （真提交、不随外层回滚消失），同一个库上先后跑完就会留下既有的 {@code RECEIVE} 日志。
     * 全表计数在那种顺序下不是「本次什么都没产生」的证据。
     */
    private int receiptLogsOfCustomer(Long customerId) {
        return count("SELECT count(*) FROM finance_operation_log l"
                + " JOIN finance_receipt r ON r.id = l.business_id"
                + " WHERE l.business_type = 'RECEIPT' AND r.customer_id = ?", customerId);
    }

    /** 切换到非超管身份取证数据范围（P0 裁决第 5 条：越权取证禁止用超管位）。 */
    private void asEmployee(Long employeeId) {
        var employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("F1-3A IT 非超管");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        SmartRequestUtil.setRequestUser(employee);
        evictMybatisCache();
    }

    private void asAdmin() {
        var employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("W5 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
        evictMybatisCache();
    }

    private Long newPlainEmployee(String tag) {
        String loginName = (prefix + "-" + tag).toUpperCase();
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder", "财务" + tag);
        Long employeeId = jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
        assertThat(jdbc.queryForObject(
                "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                .as("范围取证必须用非超管账号").isFalse();
        evictMybatisCache();
        return employeeId;
    }

    // ------------------------------------------------------------------
    // A / B / C. 三种方式正常登记
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CASH 登记：NORMAL 收款落库、单号 RC 前缀、日志 RECEIVE 且快照齐全")
    void cashReceiptWritesFactAndLog() throws Exception {
        Long customerId = customerOwnedBy(null);
        FinanceReceiptVO result = add(form(customerId, "100.0000", "CASH", PAST));

        Map<String, Object> row = receiptRow(result.getReceiptId());
        assertThat(row.get("entry_type")).isEqualTo("NORMAL");
        assertThat(row.get("reverse_of_id")).isNull();
        assertThat(row.get("reason")).isNull();
        assertThat(row.get("method")).isEqualTo("CASH");
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("100.0000");
        assertThat(row.get("customer_id")).isEqualTo(customerId);
        assertThat(row.get("deleted")).isEqualTo(false);
        assertThat(String.valueOf(row.get("receipt_no"))).matches("RC\\d{14,}");
        assertThat(row.get("created_by")).isEqualTo(currentOperator());
        assertThat(timestampOf(row.get("received_at")).toInstant()).isEqualTo(PAST.toInstant());

        List<Map<String, Object>> logs = logsOf(result.getReceiptId());
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().get("operation_type")).isEqualTo("RECEIVE");
        assertThat(logs.getFirst().get("before_data")).isNull();
        assertThat(logs.getFirst().get("operator")).isEqualTo(currentOperator());
        // after_data 按 JSON 解析而不是子串匹配：JSONB 的存储形态会重排键序并补空白，
        // 按文本断言等于把测试绑在序列化器的排版细节上。
        Map<String, Object> after = json.readValue(String.valueOf(logs.getFirst().get("after_data")),
                new TypeReference<Map<String, Object>>() {
                });
        assertThat(after)
                .containsEntry("receiptNo", result.getReceiptNo())
                .containsEntry("amount", "100.0000")
                .containsEntry("method", "CASH")
                .containsEntry("entryType", "NORMAL")
                .containsEntry("externalReference", row.get("external_reference"))
                .containsEntry("customerNameSnapshot", row.get("customer_name_snapshot"));
        assertThat(((Number) after.get("customerId")).longValue()).isEqualTo(customerId);
    }

    @Test
    @DisplayName("BANK_TRANSFER 与 OTHER 都在允许集合内，照常落库")
    void bankTransferAndOtherAreAccepted() {
        Long customerId = customerOwnedBy(null);

        assertThat(add(form(customerId, "12.5000", "BANK_TRANSFER", PAST)).getMethod())
                .isEqualTo("BANK_TRANSFER");
        assertThat(add(form(customerId, "7.0000", "OTHER", PAST)).getMethod()).isEqualTo("OTHER");

        assertThat(count("SELECT count(*) FROM finance_receipt WHERE customer_id = ?", customerId))
                .isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // D. 方式三值之外：服务层与库层双层拒绝
    // ------------------------------------------------------------------

    @Test
    @DisplayName("方式越界：服务层 41140 拒绝；直插 WECHAT 被 ck_finance_receipt_method 拒绝")
    void methodOutsideThreeValuesIsRejectedTwice() {
        Long customerId = customerOwnedBy(null);

        expectCode(() -> add(form(customerId, "10.0000", "WECHAT_PAY", PAST)), 41140);
        expectCode(() -> add(form(customerId, "10.0000", "BALANCE", PAST)), 41140);
        expectCode(() -> add(form(customerId, "10.0000", "ONLINE_PAYMENT", PAST)), 41140);
        assertThat(count("SELECT count(*) FROM finance_receipt WHERE customer_id = ?", customerId)).isZero();

        // 库层第二道：即使绕过 Java 枚举也进不来（P5 的支付方式不得混进 Finance R1）。
        expectSqlFailure("INSERT INTO finance_receipt (receipt_no, customer_id, customer_name_snapshot,"
                + " amount, method, received_at, entry_type) VALUES ('RC-D-1', ?, '客户', 10.0000,"
                + " 'WECHAT_PAY', now(), 'NORMAL')", customerId);
    }

    // ------------------------------------------------------------------
    // E / F / G / H. 金额形态与 scale
    // ------------------------------------------------------------------

    @Test
    @DisplayName("金额：0 与负数被拒；四位小数原样保存；整数补齐到 scale 4")
    void amountShapeIsNormalizedAndPositive() {
        Long customerId = customerOwnedBy(null);

        expectCode(() -> add(form(customerId, "0", "CASH", PAST)), 40000);
        expectCode(() -> add(form(customerId, "0.0000", "CASH", PAST)), 40000);
        expectCode(() -> add(form(customerId, "-5.0000", "CASH", PAST)), 40000);
        expectCode(() -> add(form(customerId, "12.34567", "CASH", PAST)), 40000);
        expectCode(() -> add(form(customerId, "1e5", "CASH", PAST)), 40000);
        expectCode(() -> add(form(customerId, "NaN", "CASH", PAST)), 40000);
        assertThat(count("SELECT count(*) FROM finance_receipt WHERE customer_id = ?", customerId)).isZero();

        // 123.4567 原样保存（不四舍五入成 123.4567 → 123.4567，也不截断）
        Long preciseId = add(form(customerId, "123.4567", "CASH", PAST)).getReceiptId();
        assertThat((BigDecimal) receiptRow(preciseId).get("amount")).isEqualByComparingTo("123.4567");
        assertThat(((BigDecimal) receiptRow(preciseId).get("amount")).scale()).as("落库 scale 恒 4").isEqualTo(4);

        // "10" → 10.0000
        Long paddedId = add(form(customerId, "10", "CASH", PAST)).getReceiptId();
        assertThat((BigDecimal) receiptRow(paddedId).get("amount")).isEqualByComparingTo("10.0000");
        assertThat(receiptRow(paddedId).get("amount")).as("NUMERIC(18,4) 的存储标度就是 4")
                .isInstanceOf(BigDecimal.class);
        assertThat(((BigDecimal) receiptRow(paddedId).get("amount")).scale()).isEqualTo(4);
    }

    // ------------------------------------------------------------------
    // I. external_reference 只是文本，重复必须放行
    // ------------------------------------------------------------------

    @Test
    @DisplayName("external_reference 重复：两次合法登记都成功，各一张收款单（它不是幂等键）")
    void duplicateExternalReferenceIsAllowed() {
        Long customerId = customerOwnedBy(null);
        // 三件全部相同：金额、凭据号、时点。真实业务里「同一账号同日两笔等额同流水号引用」就是
        // 两笔钱，任何按字段组合的自然唯一键都会把第二笔合法收款挡在库外。
        FinanceReceiptAddForm first = form(customerId, "50.0000", "BANK_TRANSFER", PAST);
        first.setExternalReference("BANK-STATEMENT-001");
        FinanceReceiptAddForm second = form(customerId, "50.0000", "BANK_TRANSFER", PAST);
        second.setExternalReference("BANK-STATEMENT-001");

        Long firstId = add(first).getReceiptId();
        Long secondId = add(second).getReceiptId();

        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(count("SELECT count(*) FROM finance_receipt WHERE customer_id = ? AND external_reference = ?",
                customerId, "BANK-STATEMENT-001")).isEqualTo(2);
        // external_reference 上有普通索引、没有唯一约束（V65）：银行流水号跨客户重复是真实存在的
        assertThat(count("SELECT count(*) FROM pg_indexes WHERE tablename = 'finance_receipt'"
                + " AND indexdef ILIKE '%unique%' AND indexdef ILIKE '%external_reference%'")).isZero();
        // 收款表根本没有来源列，所以也没有任何业务来源唯一索引可依赖；
        // 全部唯一索引只有主键、单据号与 F1-3C 的反向唯一，三者都不是业务事实幂等键。
        assertThat(jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'finance_receipt'"
                        + " AND indexdef ILIKE '%unique%'", String.class))
                .as("finance_receipt 的全部唯一索引")
                .allSatisfy(indexDef -> assertThat(indexDef)
                        .satisfiesAnyOf(
                                def -> assertThat(def).contains("receipt_no"),
                                def -> assertThat(def).contains("reverse_of_id"),
                                def -> assertThat(def).contains("finance_receipt_pkey")));
    }

    // ------------------------------------------------------------------
    // J / K. 预收与「有应收也不自动核销」
    // ------------------------------------------------------------------

    @Test
    @DisplayName("预收：客户没有任何应收也能登记收款")
    void advanceReceiptWithoutAnyReceivableIsAllowed() {
        Long customerId = customerOwnedBy(null);
        assertThat(count("SELECT count(*) FROM finance_receivable WHERE customer_id = ?", customerId)).isZero();

        FinanceReceiptVO result = add(form(customerId, "30.0000", "CASH", PAST));

        assertThat(result.getReceiptNo()).startsWith("RC");
        assertThat(count("SELECT count(*) FROM finance_write_off WHERE source_type = 'RECEIPT'"))
                .as("核销是 F1-4 的能力，全库都不该有一行").isZero();
        // 按客户收窄而不是全表：同一库里 NOT_SUPPORTED 的 IT 会提交真实的应收事实
        assertThat(count("SELECT count(*) FROM finance_receivable WHERE customer_id = ?", customerId))
                .as("登记收款不产生任何应收").isZero();
    }

    @Test
    @DisplayName("已有应收时登记收款：不生成核销行、不改应收单与订单，只多一笔待核销款")
    void registeringReceiptNeverWritesOffOrTouchesReceivable() {
        Long customerId = customerOwnedBy(null);
        Long orderId = confirmedSalesOrder(customerId, newOnShelfSku("RCT"), "10.0000", "10.0000");
        // 受控夹具：F1-5 的应收查询/派生尚未实现，此处按 schema 直插一张正常应收事实，
        // 目的是证明「收款登记不会碰它」，不依赖也不提前实现 F1-4 的核销能力。
        jdbc.update("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id,"
                        + " customer_id, customer_name_snapshot, entry_type, amount, event_at,"
                        + " created_at, updated_at, created_by)"
                        + " VALUES ('AR-FIXTURE-1', 'SALES_ORDER', ?, ?, ?, '测试客户', 'NORMAL', 12.0000,"
                        + " now(), now(), now(), ?)",
                orderId, orderId, customerId, currentOperator());
        evictMybatisCache();
        Map<String, Object> before = jdbc.queryForMap(
                "SELECT * FROM finance_receivable WHERE source_id = ? AND entry_type = 'NORMAL'", orderId);
        BigDecimal orderSettlementBefore = jdbc.queryForObject(
                "SELECT settlement_total_amount FROM sales_order WHERE id = ?", BigDecimal.class, orderId);

        add(form(customerId, "5.0000", "CASH", PAST));

        Map<String, Object> after = jdbc.queryForMap(
                "SELECT * FROM finance_receivable WHERE source_id = ? AND entry_type = 'NORMAL'", orderId);
        assertThat(after.get("amount")).isEqualTo(before.get("amount"));
        assertThat(after.get("version")).isEqualTo(before.get("version"));
        assertThat(after.get("updated_at")).isEqualTo(before.get("updated_at"));
        assertThat(count("SELECT count(*) FROM finance_write_off")).as("F1-4 能力，绝不提前产生").isZero();
        assertThat(count("SELECT count(*) FROM finance_receipt WHERE customer_id = ?", customerId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT settlement_total_amount FROM sales_order WHERE id = ?",
                BigDecimal.class, orderId)).as("订单结算金额一字未改").isEqualByComparingTo(orderSettlementBefore);
    }

    // ------------------------------------------------------------------
    // L / M. 幂等重放与冲突
    // ------------------------------------------------------------------

    @Test
    @DisplayName("幂等重放：同键同内容返回同一张收款单，库里一张、日志一条")
    void sameKeyAndContentReplaysTheFirstResult() {
        Long customerId = customerOwnedBy(null);
        FinanceReceiptAddForm form = form(customerId, "88.0000", "CASH", PAST);
        String idempotencyKey = key("replay");

        FinanceReceiptVO first = financeReceiptService.add(form, idempotencyKey);
        FinanceReceiptVO replay = financeReceiptService.add(form, idempotencyKey);

        assertThat(replay.getReceiptId()).isEqualTo(first.getReceiptId());
        assertThat(replay.getReceiptNo()).isEqualTo(first.getReceiptNo());
        assertThat(replay.getAmount()).isEqualByComparingTo(first.getAmount());
        assertThat(count("SELECT count(*) FROM finance_receipt WHERE customer_id = ?", customerId)).isEqualTo(1);
        assertThat(logsOf(first.getReceiptId())).hasSize(1);
        assertThat(count("SELECT count(*) FROM idempotency_record WHERE idempotency_key = ?", idempotencyKey))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("幂等冲突：同键不同金额 → 40966，不产生第二张收款单")
    void sameKeyDifferentContentIsRejectedAsConflict() {
        Long customerId = customerOwnedBy(null);
        String idempotencyKey = key("conflict");
        financeReceiptService.add(form(customerId, "10.0000", "CASH", PAST), idempotencyKey);

        FinanceReceiptAddForm different = form(customerId, "99.0000", "CASH", PAST);
        expectCode(() -> financeReceiptService.add(different, idempotencyKey), 40966);

        assertThat(count("SELECT count(*) FROM finance_receipt WHERE customer_id = ?", customerId)).isEqualTo(1);
        assertThat(receiptLogsOfCustomer(customerId)).as("冲突的那一次没有追加日志").isEqualTo(1);
    }

    @Test
    @DisplayName("缺 Idempotency-Key：40069 拒绝，什么都不产生")
    void missingIdempotencyKeyIsRejected() {
        Long customerId = customerOwnedBy(null);
        FinanceReceiptAddForm form = form(customerId, "10.0000", "CASH", PAST);

        expectCode(() -> financeReceiptService.add(form, "  "), 40069);
        assertThat(count("SELECT count(*) FROM finance_receipt WHERE customer_id = ?", customerId)).isZero();
    }

    // ------------------------------------------------------------------
    // N. 客户名称快照冻结
    // ------------------------------------------------------------------

    @Test
    @DisplayName("登记后改客户名称：收款单上的名称快照不变（历史账不回读主档）")
    void customerNameSnapshotIsFrozenAtRegistration() {
        Long customerId = customerOwnedBy(null);
        String original = jdbc.queryForObject("SELECT name FROM customer WHERE id = ?", String.class, customerId);

        Long receiptId = add(form(customerId, "20.0000", "CASH", PAST)).getReceiptId();
        jdbc.update("UPDATE customer SET name = ? WHERE id = ?", "改名后的客户" + UUID.randomUUID(), customerId);
        evictMybatisCache();

        assertThat(receiptRow(receiptId).get("customer_name_snapshot")).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT name FROM customer WHERE id = ?", String.class, customerId))
                .as("主档确实已经改名，快照才有判别性").startsWith("改名后的客户");
    }

    // ------------------------------------------------------------------
    // O / P / Q. 数据范围（第三批 D-5）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("范围正向：客户归属本人（非超管、无 scope:all）即可登记")
    void ownCustomerIsRegistrableForNonAdministrator() {
        Long employeeId = newPlainEmployee("O");
        Long customerId = customerOwnedBy(employeeId);
        try {
            asEmployee(employeeId);
            FinanceReceiptVO result = add(form(customerId, "15.0000", "CASH", PAST));
            assertThat(receiptRow(result.getReceiptId()).get("customer_id")).isEqualTo(customerId);
        } finally {
            asAdmin();
        }
    }

    @Test
    @DisplayName("范围越界：客户属于别的业务员 → 30005 信封，收款单 0 条")
    void otherSellersCustomerIsRejectedWithoutLeakingExistence() {
        Long employeeId = newPlainEmployee("P");
        Long otherSeller = newPlainEmployee("P-OTHER");
        Long customerId = customerOwnedBy(otherSeller);
        try {
            asEmployee(employeeId);
            // 与「客户不存在」共用同一个异常：能分辨存在与否就等于把主键探测变成可用信号
            assertThatThrownBy(() -> add(form(customerId, "15.0000", "CASH", PAST)))
                    .isInstanceOf(ScmDataScopeException.class);
            assertThat(count("SELECT count(*) FROM finance_receipt WHERE customer_id = ?", customerId)).isZero();
            assertThat(receiptLogsOfCustomer(customerId)).isZero();

            // 不存在的客户必须是同一个异常
            assertThatThrownBy(() -> add(form(999999999L, "15.0000", "CASH", PAST)))
                    .isInstanceOf(ScmDataScopeException.class);
        } finally {
            asAdmin();
        }
    }

    @Test
    @DisplayName("未分配业务员的客户：非超管不可代其登记（失败关闭，不放宽成「都能收」）")
    void unassignedCustomerIsFailClosedForNonAdministrator() {
        Long employeeId = newPlainEmployee("Q");
        Long customerId = customerOwnedBy(null);
        try {
            asEmployee(employeeId);
            assertThatThrownBy(() -> add(form(customerId, "15.0000", "CASH", PAST)))
                    .isInstanceOf(ScmDataScopeException.class);
            assertThat(count("SELECT count(*) FROM finance_receipt WHERE customer_id = ?", customerId)).isZero();
        } finally {
            asAdmin();
            // 全范围账号（超管兜底路径）可以登记，证明拒绝来自范围而不是字段形态
            assertThat(add(form(customerId, "15.0000", "CASH", PAST)).getReceiptId()).isNotNull();
        }
    }

    // ------------------------------------------------------------------
    // R / S. received_at 由登记人填写
    // ------------------------------------------------------------------

    @Test
    @DisplayName("received_at：显式业务时点逐值落库，服务端不改成 now()")
    void receivedAtIsTheCallerSuppliedBusinessMoment() {
        Long customerId = customerOwnedBy(null);
        Long receiptId = add(form(customerId, "40.0000", "BANK_TRANSFER", PAST)).getReceiptId();

        assertThat(timestampOf(receiptRow(receiptId).get("received_at")).toInstant())
                .isEqualTo(PAST.toInstant());
        // 库侧逐值判等（不是「接近」）：服务端一旦改成 now()，这里立刻为 false
        assertThat(jdbc.queryForObject(
                "SELECT received_at = ?::timestamptz FROM finance_receipt WHERE id = ?",
                Boolean.class, PAST.toInstant().toString(), receiptId)).isTrue();
    }

    @Test
    @DisplayName("未来时点不被拒绝：本期没有「不得晚于当前时间」这条规则")
    void futureReceivedAtIsNotRejected() {
        Long customerId = customerOwnedBy(null);
        // 夹具取到微秒：timestamptz 只有微秒精度，纳秒会在服务端被舍入，
        // 归一放在夹具侧才能让下面这条逐值判等保持严格。
        OffsetDateTime future = OffsetDateTime.now().plusDays(30).truncatedTo(ChronoUnit.MICROS);

        Long receiptId = add(form(customerId, "10.0000", "CASH", future)).getReceiptId();

        assertThat(timestampOf(receiptRow(receiptId).get("received_at")).toInstant())
                .isEqualTo(future.toInstant());
    }

    private static String currentOperator() {
        return net.lab1024.sa.admin.module.scm.common.constant.ScmOperator.current();
    }

    /**
     * {@code timestamptz} 在 {@code queryForMap} 的 Map 读法里返回 {@link java.sql.Timestamp}，
     * 类型化读法返回 {@link OffsetDateTime}；统一成 {@code toInstant()} 才比较的是同一个时刻。
     */
    private static OffsetDateTime timestampOf(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        throw new IllegalArgumentException("无法归一为 OffsetDateTime 的列类型: "
                + (value == null ? "null" : value.getClass().getName()));
    }

}
