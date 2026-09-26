package net.lab1024.sa.admin.module.scm.finance;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceCounterpartyTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceEntryTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceOperationTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinancePayableItemSourceTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinancePayableSourceTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinancePaymentMethodEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinancePaymentSourceTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceReceivableItemSourceTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceReceivableSourceTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceReverseEntryTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceWriteOffSourceTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceWriteOffTargetTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V65 的 schema 契约与 F1-1 的阶段边界（F1-1 唯一新增的 PG IT，设计稿 §22.1）。
 *
 * <p>本类是 Finance R1 **唯一直接断言 schema 形状**的地方（表 / 序列 / 列 / 约束 / 索引谓词），
 * 其余财务 IT 只断言行为。schema 一旦被后续阶段悄悄改动，这里会先失败
 * （与 {@code ScmInventoryMigrationIT} / {@code ScmPurchaseMigrationIT} 同一分工）。
 *
 * <p><b>只测 F1-1 的契约，不提前写 F1-2 的业务 IT</b>：本类不生成任何应收 / 应付，
 * 只验证「库会把不合法的事实挡在外面」，以及「本阶段没有越界发布菜单与权限」
 * （见 {@link #financePublishesNoMenuOrPermissionYet()}）。
 *
 * <p><b>不修改任何数据</b>：只读元数据 + 用 {@code expectSqlFailure}（SAVEPOINT 隔离）
 * 验证约束真的会拒绝坏数据。用例整体在一个事务里，结束回滚。
 */
@DisplayName("Finance R1 迁移契约与阶段边界（PG IT）")
class ScmFinanceSchemaPgIT extends ScmW6PgITBase {

    /**
     * V65 建的 8 张表（设计稿 §2 对象清单）。
     */
    private static final List<String> FINANCE_TABLES = List.of(
            "finance_receivable", "finance_receivable_item",
            "finance_payable", "finance_payable_item",
            "finance_receipt", "finance_payment",
            "finance_write_off", "finance_operation_log");

    /**
     * 七张带 {@code deleted} 列的事实表；{@code finance_operation_log} 刻意不在其中 ——
     * 它连 {@code deleted} 列都没有，append-only 是结构性的。
     */
    private static final List<String> APPEND_ONLY_TABLES = List.of(
            "finance_receivable", "finance_receivable_item",
            "finance_payable", "finance_payable_item",
            "finance_receipt", "finance_payment", "finance_write_off");

    private static final List<String> FINANCE_SEQUENCES = List.of(
            "finance_receivable_no_seq", "finance_payable_no_seq", "finance_receipt_no_seq",
            "finance_payment_no_seq", "finance_write_off_no_seq");

    private String no(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 16).toUpperCase(java.util.Locale.ROOT);
    }

    private String constraintDef(String table, String constraint) {
        return jdbc.queryForObject(
                "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c "
                        + "JOIN pg_class t ON t.oid = c.conrelid "
                        + "WHERE t.relname = ? AND c.conname = ?",
                String.class, table, constraint);
    }

    private List<String> columnsOf(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = ?",
                String.class, table);
    }

    private String indexDef(String index) {
        return jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = current_schema() AND indexname = ?",
                String.class, index);
    }

    /**
     * 从约束定义里抽出全部单引号字面量，用于比对 Java enum 与 DB CHECK 白名单是否同一份真值。
     *
     * <p>模式刻意允许空串（{@code [^']*}）：配对类约束里有 {@code btrim(COALESCE(reason, '')) <> ''}，
     * 若要求至少一个字符，正则会跨过 {@code ''} 去匹配到 {@code ') <> '} 这种伪字面量。
     * 空串随后被过滤掉，因此只留下真正的枚举取值。
     */
    private Set<String> literalsOf(String constraintDef) {
        Matcher matcher = Pattern.compile("'([^']*)'").matcher(constraintDef);
        return matcher.results()
                .map(r -> r.group(1))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    // ------------------------------------------------------------------
    // 表 / 序列 / 列
    // ------------------------------------------------------------------

    @Test
    @DisplayName("V65 建 8 表 + 5 序列，且不含任何外键")
    void tablesSequencesAndNoForeignKeys() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'", String.class);
        assertThat(tables).containsAll(FINANCE_TABLES);

        List<String> sequences = jdbc.queryForList(
                "SELECT sequencename FROM pg_sequences WHERE schemaname = current_schema()", String.class);
        assertThat(sequences).containsAll(FINANCE_SEQUENCES);

        // 项目级口径：不建数据库外键，关系完整性由服务层 + CHECK + 唯一索引承担。
        Integer foreignKeys = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid "
                        + "WHERE c.contype = 'f' AND t.relname LIKE 'finance_%'", Integer.class);
        assertThat(foreignKeys).isZero();
    }

    @Test
    @DisplayName("财务表不含状态列 / 余额列 / 账期列（Q15 Q17 Q20，全局不变量 6）")
    void noStateMachineOrDerivedColumns() {
        // 落库的派生态一定会漂移；due_date 与审批字段属 R2 或未裁决范围。
        List<String> forbidden = List.of(
                "status", "settled_amount", "open_amount", "written_off_amount", "net_amount",
                "due_date", "approver", "approval_state", "currency", "tax_rate", "tax_amount");
        for (String table : FINANCE_TABLES) {
            assertThat(columnsOf(table))
                    .as("表 %s 不得出现派生态或越界列", table)
                    .doesNotContainAnyElementsOf(forbidden);
        }
        // 方向编码在类型里，因此不存在独立的 direction 列（照 inventory_movement 的纪律）。
        for (String table : FINANCE_TABLES) {
            assertThat(columnsOf(table)).doesNotContain("direction");
        }
    }

    @Test
    @DisplayName("七张事实表带 append-only CHECK，软删在库级被拒（设计稿 §0 第 7 条）")
    void appendOnlyCheckRejectsSoftDelete() {
        for (String table : APPEND_ONLY_TABLES) {
            assertThat(constraintDef(table, "ck_" + table + "_append_only"))
                    .as("%s 的 append-only CHECK", table)
                    .containsIgnoringCase("deleted = false");
        }
        // finance_operation_log 刻意不设 deleted 列：没有可翻转的标记，就没有删除入口。
        assertThat(columnsOf("finance_operation_log")).doesNotContain("deleted", "version");
    }

    // ------------------------------------------------------------------
    // CHECK：金额 / 数量 / 精度
    // ------------------------------------------------------------------

    @Test
    @DisplayName("金额恒 > 0、数量恒 > 0、单价 >= 0、version >= 0（Q22，全局不变量 7）")
    void amountAndQuantityChecks() {
        long id = insertReceivable("AR", "SALES_ORDER", "NORMAL", null, null, new BigDecimal("100.0000"));

        // 金额与数量恒正：0 元事实不允许存在（Q8「不生成 0 元财务事实」的库级兜底）。
        expectSqlFailure("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id, "
                        + "customer_id, customer_name_snapshot, entry_type, amount, event_at) "
                        + "VALUES (?, 'SALES_ORDER', 900001, 900001, 900001, '客户', 'NORMAL', 0, now())",
                no("AR"));
        expectSqlFailure("INSERT INTO finance_receivable_item (receivable_id, source_type, source_id, "
                        + "order_item_id, sku_id, sku_name_snapshot, unit_snapshot, quantity, unit_price, amount) "
                        + "VALUES (?, 'INVENTORY_OUTBOUND_ITEM', 900001, 900001, 900001, '商品', 'kg', 0, 1, 0)",
                id);
        expectSqlFailure("INSERT INTO finance_receivable_item (receivable_id, source_type, source_id, "
                        + "order_item_id, sku_id, sku_name_snapshot, unit_snapshot, quantity, unit_price, amount) "
                        + "VALUES (?, 'INVENTORY_OUTBOUND_ITEM', 900002, 900001, 900001, '商品', 'kg', 1, -1, -1)",
                id);
        expectSqlFailure("UPDATE finance_receivable SET version = -1 WHERE id = ?", id);

        // 单价允许为 0（赠品口径），但明细金额因此为 0 时**单头**不会生成，见 F1-2 生成器。
        jdbc.update("INSERT INTO finance_receivable_item (receivable_id, source_type, source_id, order_item_id, "
                        + "sku_id, sku_name_snapshot, unit_snapshot, quantity, unit_price, amount) "
                        + "VALUES (?, 'INVENTORY_OUTBOUND_ITEM', 900003, 900001, 900001, '商品', 'kg', 1, 0, 0)",
                id);

        // 全表金额精度恒为 NUMERIC(18,4)（Q22）：本期不新增 2 位财务存储体系。
        for (String table : FINANCE_TABLES) {
            List<String> moneyColumns = jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = current_schema() AND table_name = ? "
                            + "AND data_type = 'numeric'", String.class, table);
            for (String column : moneyColumns) {
                Integer scale = jdbc.queryForObject(
                        "SELECT numeric_scale FROM information_schema.columns "
                                + "WHERE table_schema = current_schema() AND table_name = ? "
                                + "AND column_name = ?", Integer.class, table, column);
                Integer precision = jdbc.queryForObject(
                        "SELECT numeric_precision FROM information_schema.columns "
                                + "WHERE table_schema = current_schema() AND table_name = ? "
                                + "AND column_name = ?", Integer.class, table, column);
                assertThat(precision).as("%s.%s 精度", table, column).isEqualTo(18);
                assertThat(scale).as("%s.%s 小数位", table, column).isEqualTo(4);
            }
        }
    }

    // ------------------------------------------------------------------
    // CHECK：NORMAL / RED 与 NORMAL / REVERSE 的字段配对
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应收 NORMAL/RED 配对：红字必须引用原单并填原因，正常应收两者必须为空")
    void receivableEntryPairing() {
        assertThat(constraintDef("finance_receivable", "ck_finance_receivable_entry_pairing"))
                .contains("NORMAL").contains("RED").contains("original_receivable_id");
        assertThat(constraintDef("finance_receivable", "ck_finance_receivable_source_pairing"))
                .contains("SALES_ORDER").contains("ORDER_RETURN");

        // 红字缺原单引用 → 拒绝（Q13 / Q27：红字必须可追溯到被冲的原应收）。
        expectSqlFailure("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id, "
                        + "customer_id, customer_name_snapshot, entry_type, amount, event_at, reason) "
                        + "VALUES (?, 'ORDER_RETURN', 900002, 900001, 900001, '客户', 'RED', 20, now(), '退货')",
                no("AR"));
        // 红字缺原因 → 拒绝。
        expectSqlFailure("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id, "
                        + "customer_id, customer_name_snapshot, entry_type, amount, event_at, "
                        + "original_receivable_id, reason) "
                        + "VALUES (?, 'ORDER_RETURN', 900003, 900001, 900001, '客户', 'RED', 20, now(), 1, '  ')",
                no("AR"));
        // 正常应收却带原单引用 → 拒绝（方向与引用必须一致，否则读时派生会算错净额）。
        expectSqlFailure("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id, "
                        + "customer_id, customer_name_snapshot, entry_type, amount, event_at, original_receivable_id) "
                        + "VALUES (?, 'SALES_ORDER', 900004, 900001, 900001, '客户', 'NORMAL', 30, now(), 1)",
                no("AR"));
        // 方向与来源错配（NORMAL 却声明来源是退货）→ 拒绝。
        expectSqlFailure("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id, "
                        + "customer_id, customer_name_snapshot, entry_type, amount, event_at) "
                        + "VALUES (?, 'ORDER_RETURN', 900005, 900001, 900001, '客户', 'NORMAL', 30, now())",
                no("AR"));
    }

    @Test
    @DisplayName("应付 MANUAL 红字配对：source_id 必须 NULL、原单与原因必填（设计稿 §3.1 修正）")
    void payableManualRedPairing() {
        assertThat(constraintDef("finance_payable", "ck_finance_payable_source_pairing"))
                .contains("PURCHASE_RECEIPT").contains("MANUAL").contains("source_id IS NULL");

        // 手工红字却带 source_id → 拒绝：那会让它落进来源唯一索引的谓词内，
        // 与「手工事实没有外部业务行」的模型矛盾。
        expectSqlFailure("INSERT INTO finance_payable (payable_no, source_type, source_id, purchase_order_id, "
                        + "supplier_id, supplier_name_snapshot, entry_type, original_payable_id, amount, event_at, "
                        + "reason) VALUES (?, 'MANUAL', 900009, 900001, 900001, '供应商', 'RED', 1, 10, now(), '录错')",
                no("AP"));
        // 手工红字缺原因 → 拒绝（Q13）。
        expectSqlFailure("INSERT INTO finance_payable (payable_no, source_type, purchase_order_id, supplier_id, "
                        + "supplier_name_snapshot, entry_type, original_payable_id, amount, event_at) "
                        + "VALUES (?, 'MANUAL', 900001, 900001, '供应商', 'RED', 1, 10, now())",
                no("AP"));
        // 正常应付缺 source_id → 拒绝：正常应付必须能追溯到收货单。
        expectSqlFailure("INSERT INTO finance_payable (payable_no, source_type, purchase_order_id, supplier_id, "
                        + "supplier_name_snapshot, entry_type, amount, event_at) "
                        + "VALUES (?, 'PURCHASE_RECEIPT', 900001, 900001, '供应商', 'NORMAL', 10, now())",
                no("AP"));

        // 明细同形：MANUAL 明细的 source_id 必须为 NULL。
        assertThat(constraintDef("finance_payable_item", "ck_finance_payable_item_source_pairing"))
                .contains("PURCHASE_RECEIPT_ITEM").contains("MANUAL");
        expectSqlFailure("INSERT INTO finance_payable_item (payable_id, source_type, source_id, "
                        + "purchase_order_item_id, sku_id, sku_name_snapshot, unit_snapshot, quantity, unit_price, "
                        + "amount) VALUES (900001, 'MANUAL', 900009, 900001, 900001, '商品', 'kg', 1, 1, 1)",
                no("AP"));
    }

    @Test
    @DisplayName("D-3：收付款反向三列配对，且反向付款的来源必须为 NULL")
    void receiptPaymentReversePairing() {
        for (String table : List.of("finance_receipt", "finance_payment")) {
            assertThat(constraintDef(table, "ck_" + table + "_entry_pairing"))
                    .as("%s 的反向配对 CHECK", table)
                    .contains("NORMAL").contains("REVERSE").contains("reverse_of_id");
        }
        assertThat(constraintDef("finance_payment", "ck_finance_payment_reverse_no_source"))
                .contains("NORMAL").contains("source_type IS NULL");
        assertThat(constraintDef("finance_payment", "ck_finance_payment_source_pairing"))
                .contains("ORDER_REFUND");

        long receipt = insertReceipt(no("RC"), "NORMAL", null, null, null);
        // 反向行缺原因 → 拒绝。
        expectSqlFailure("INSERT INTO finance_receipt (receipt_no, customer_id, customer_name_snapshot, amount, "
                        + "method, received_at, entry_type, reverse_of_id) "
                        + "VALUES (?, 900001, '客户', 100, 'CASH', now(), 'REVERSE', ?)", no("RC"), receipt);
        // 正常行却带 reverse_of_id → 拒绝。
        expectSqlFailure("INSERT INTO finance_receipt (receipt_no, customer_id, customer_name_snapshot, amount, "
                        + "method, received_at, entry_type, reverse_of_id, reason) "
                        + "VALUES (?, 900001, '客户', 100, 'CASH', now(), 'NORMAL', ?, '多余')", no("RC"), receipt);

        // 反向付款沿用 ORDER_REFUND 来源 → 拒绝。这条 CHECK 挡住的是「纠错路径被自己的
        // 防重索引锁死」：uk_finance_payment_source_active 的谓词是 source_id IS NOT NULL，
        // 反向行若带来源就会与原行抢同一个键，于是登错的退款付款再也反向不掉。
        expectSqlFailure("INSERT INTO finance_payment (payment_no, counterparty_type, counterparty_id, "
                        + "counterparty_name_snapshot, amount, method, paid_at, entry_type, reverse_of_id, reason, "
                        + "source_type, source_id) "
                        + "VALUES (?, 'CUSTOMER', 900001, '客户', 100, 'CASH', now(), 'REVERSE', 1, '登错', "
                        + "'ORDER_REFUND', 900001)", no("PM"));
        // source_type 与 source_id 只填一半 → 拒绝。
        expectSqlFailure("INSERT INTO finance_payment (payment_no, counterparty_type, counterparty_id, "
                        + "counterparty_name_snapshot, amount, method, paid_at, entry_type, source_type) "
                        + "VALUES (?, 'CUSTOMER', 900001, '客户', 100, 'CASH', now(), 'NORMAL', 'ORDER_REFUND')",
                no("PM"));
    }

    @Test
    @DisplayName("核销配对：收款只核应收、付款只核应付（Q17）")
    void writeOffPairing() {
        assertThat(constraintDef("finance_write_off", "ck_finance_write_off_pairing"))
                .contains("RECEIPT").contains("RECEIVABLE").contains("PAYMENT").contains("PAYABLE");
        assertThat(constraintDef("finance_write_off", "ck_finance_write_off_entry_pairing"))
                .contains("NORMAL").contains("REVERSE");

        expectSqlFailure("INSERT INTO finance_write_off (write_off_no, source_type, source_id, target_type, "
                        + "target_id, amount, entry_type, written_off_at, operator) "
                        + "VALUES (?, 'RECEIPT', 1, 'PAYABLE', 1, 10, 'NORMAL', now(), '2:1')", no("WO"));
        expectSqlFailure("INSERT INTO finance_write_off (write_off_no, source_type, source_id, target_type, "
                        + "target_id, amount, entry_type, written_off_at, operator) "
                        + "VALUES (?, 'PAYMENT', 1, 'RECEIVABLE', 1, 10, 'NORMAL', now(), '2:1')", no("WO"));
    }

    @Test
    @DisplayName("操作日志的 business_type / operation_type 白名单与 JSONB 形状")
    void operationLogWhitelist() {
        assertThat(constraintDef("finance_operation_log", "ck_finance_operation_log_business_type"))
                .contains("RECEIVABLE").contains("PAYABLE").contains("RECEIPT").contains("PAYMENT")
                .contains("WRITE_OFF");
        String typeDef = constraintDef("finance_operation_log", "ck_finance_operation_log_type");
        // 八个值就是 F1-1 的最终范围，含 D-3 带来的两个反向类型。
        assertThat(literalsOf(typeDef)).containsExactlyInAnyOrder(
                Stream.of(ScmFinanceOperationTypeEnum.values())
                        .map(Enum::name).toArray(String[]::new));
        assertThat(constraintDef("finance_operation_log", "ck_finance_operation_log_before_data"))
                .contains("jsonb_typeof");
        assertThat(constraintDef("finance_operation_log", "ck_finance_operation_log_after_data"))
                .contains("jsonb_typeof");

        expectSqlFailure("INSERT INTO finance_operation_log (business_type, business_id, operation_type, operator) "
                + "VALUES ('RECEIVABLE', 1, 'DELETE', '2:1')");
        expectSqlFailure("INSERT INTO finance_operation_log (business_type, business_id, operation_type, operator) "
                + "VALUES ('INVOICE', 1, 'GENERATE', '2:1')");
        // JSONB 列只接受 object：数组或标量会让「改前 / 改后金额级证据」失去可比对的结构。
        expectSqlFailure("INSERT INTO finance_operation_log (business_type, business_id, operation_type, operator, "
                + "after_data) VALUES ('RECEIVABLE', 1, 'GENERATE', '2:1', '[1,2]'::jsonb)");
    }

    // ------------------------------------------------------------------
    // 唯一索引：来源防重、反向防重、以及刻意**不**唯一的两处
    // ------------------------------------------------------------------

    @Test
    @DisplayName("来源唯一索引存在且谓词逐字正确（Q26：来源唯一索引是最终数据库防线）")
    void sourceUniqueIndexPredicates() {
        assertThat(indexDef("uk_finance_receivable_source_active"))
                .contains("CREATE UNIQUE").contains("source_type, source_id").contains("WHERE (deleted = false)");
        assertThat(indexDef("uk_finance_receivable_item_source_active"))
                .contains("CREATE UNIQUE").contains("source_type, source_id").contains("WHERE (deleted = false)");
        // 应付 / 应付明细 / 付款的谓词必须带 source_id IS NOT NULL：手工红字、MANUAL 明细与
        // 无来源预付的 source_id 为 NULL，而 NULL 不等于任何值 —— 少了这个谓词，
        // PostgreSQL 会放行任意多条手工红字，索引形同不存在（设计稿 §3.1 / §11）。
        for (String index : List.of("uk_finance_payable_source_active",
                "uk_finance_payable_item_source_active", "uk_finance_payment_source_active")) {
            assertThat(indexDef(index))
                    .as("%s 的谓词", index)
                    .contains("CREATE UNIQUE")
                    .contains("WHERE ((deleted = false) AND (source_id IS NOT NULL))");
        }
    }

    @Test
    @DisplayName("来源唯一索引真的挡住重复财务事实")
    void sourceUniqueIndexRejectsDuplicateFact() {
        long first = insertReceivable("AR", "SALES_ORDER", "NORMAL", null, null, new BigDecimal("100.0000"));
        Long sourceId = jdbc.queryForObject("SELECT source_id FROM finance_receivable WHERE id = ?",
                Long.class, first);
        // 同一张订单第二次生成应收 → 拒绝。生成器据此走 insertOnConflictDoNothing，
        // 命中冲突即「已生成」并返回成功（可重放的派生，不是用户命令）。
        expectSqlFailure("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id, "
                        + "customer_id, customer_name_snapshot, entry_type, amount, event_at) "
                        + "VALUES (?, 'SALES_ORDER', ?, 900001, 900001, '客户', 'NORMAL', 100, now())",
                no("AR"), sourceId);
    }

    @Test
    @DisplayName("三条反向唯一索引：一条 NORMAL 最多被反向一次（Q18 / D-3）")
    void singleReverseUniqueIndexes() {
        for (String index : List.of("uk_finance_write_off_single_reverse",
                "uk_finance_receipt_single_reverse", "uk_finance_payment_single_reverse")) {
            // 断言谓词的**语义**而不是逐字文本：PostgreSQL 会把 varchar 比较渲染成
            // ((entry_type)::text = 'REVERSE'::text)，逐字比对等于把断言绑在 PG 版本的
            // 反解析格式上。三个成分齐备才是「只约束反向行」这件事的实质。
            assertThat(indexDef(index))
                    .as("%s 的定义", index)
                    .contains("CREATE UNIQUE")
                    .contains("reverse_of_id")
                    .contains("deleted = false")
                    .contains("'REVERSE'");
        }

        long receipt = insertReceipt(no("RC"), "NORMAL", null, null, null);
        insertReceipt(no("RC"), "REVERSE", receipt, "登错金额", null);
        // 第二条反向 → 撞库级唯一索引。重复撤销必须在这里失败，而不是靠服务层先查后判。
        expectSqlFailure("INSERT INTO finance_receipt (receipt_no, customer_id, customer_name_snapshot, amount, "
                        + "method, received_at, entry_type, reverse_of_id, reason) "
                        + "VALUES (?, 900001, '客户', 100, 'CASH', now(), 'REVERSE', ?, '再冲一次')",
                no("RC"), receipt);
    }

    @Test
    @DisplayName("external_reference 刻意**不**唯一，且不作幂等键（设计稿 §3.2 修正）")
    void externalReferenceIsNotUnique() {
        // 银行流水号跨客户重复是真实存在的；把它当唯一键会让第二笔合法收款登不进去。
        // 本断言防止有人「顺手」把 UNIQUE 加回去。
        for (String index : List.of("idx_finance_receipt_external_ref", "idx_finance_payment_external_ref")) {
            assertThat(indexDef(index)).doesNotContain("CREATE UNIQUE");
        }
        String shared = "BANK-" + UUID.randomUUID();
        insertReceipt(no("RC"), "NORMAL", null, null, shared);
        // 同一凭据号的第二笔收款必须成功。
        insertReceipt(no("RC"), "NORMAL", null, null, shared);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_receipt WHERE external_reference = ?", Integer.class, shared))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("手工红字应付不受来源唯一索引约束（source_id 为 NULL 落在谓词之外）")
    void manualRedPayableNotBlockedBySourceUniqueIndex() {
        // 两张手工红字就是两个事实；超额由 41137 拦，请求级幂等键防重复提交。
        insertPayable("AP", "MANUAL", null, "RED", "录错单价");
        insertPayable("AP", "MANUAL", null, "RED", "录错数量");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_payable WHERE source_type = 'MANUAL' AND source_id IS NULL",
                Integer.class)).isGreaterThanOrEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Java enum ↔ DB CHECK 白名单同源
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Java enum 与 DB CHECK 白名单逐值一致（Q21：enum + CHECK，不用 t_dict）")
    void enumValuesMatchDatabaseCheckWhitelist() {
        // 每个枚举都对上一条** dedicated **的单列 CHECK，因此可以断言双向相等：
        // 少一个值意味着 Java 侧能造出库里不接受的事实，多一个值意味着库里放行了
        // 一个 Java 侧无法表达（因而也无法在页面上解释）的取值。
        assertWhitelist("finance_receivable", "ck_finance_receivable_source_type",
                ScmFinanceReceivableSourceTypeEnum.class);
        assertWhitelist("finance_receivable_item", "ck_finance_receivable_item_source_type",
                ScmFinanceReceivableItemSourceTypeEnum.class);
        assertWhitelist("finance_payable", "ck_finance_payable_source_type",
                ScmFinancePayableSourceTypeEnum.class);
        assertWhitelist("finance_payable_item", "ck_finance_payable_item_source_type",
                ScmFinancePayableItemSourceTypeEnum.class);
        assertWhitelist("finance_payment", "ck_finance_payment_counterparty_type",
                ScmFinanceCounterpartyTypeEnum.class);
        assertWhitelist("finance_payment", "ck_finance_payment_source_type",
                ScmFinancePaymentSourceTypeEnum.class);
        assertWhitelist("finance_write_off", "ck_finance_write_off_source_type",
                ScmFinanceWriteOffSourceTypeEnum.class);
        assertWhitelist("finance_write_off", "ck_finance_write_off_target_type",
                ScmFinanceWriteOffTargetTypeEnum.class);
        assertWhitelist("finance_operation_log", "ck_finance_operation_log_business_type",
                ScmFinanceBusinessTypeEnum.class);
        assertWhitelist("finance_operation_log", "ck_finance_operation_log_type",
                ScmFinanceOperationTypeEnum.class);

        // 收付款方式固定三值，且收款与付款共用同一套枚举（Q21）。
        assertWhitelist("finance_receipt", "ck_finance_receipt_method", ScmFinancePaymentMethodEnum.class);
        assertWhitelist("finance_payment", "ck_finance_payment_method", ScmFinancePaymentMethodEnum.class);

        // NORMAL / RED 用于应收应付，NORMAL / REVERSE 用于收付款与核销；两套刻意不合并。
        assertWhitelist("finance_receivable", "ck_finance_receivable_entry_type", ScmFinanceEntryTypeEnum.class);
        assertWhitelist("finance_payable", "ck_finance_payable_entry_type", ScmFinanceEntryTypeEnum.class);
        assertWhitelist("finance_receipt", "ck_finance_receipt_entry_type",
                ScmFinanceReverseEntryTypeEnum.class);
        assertWhitelist("finance_payment", "ck_finance_payment_entry_type",
                ScmFinanceReverseEntryTypeEnum.class);
        assertWhitelist("finance_write_off", "ck_finance_write_off_entry_type",
                ScmFinanceReverseEntryTypeEnum.class);

        // 方式枚举不得混入任何 P5 支付能力（Q21）。
        assertThat(literalsOf(constraintDef("finance_receipt", "ck_finance_receipt_method")))
                .doesNotContain("ONLINE_PAYMENT", "BALANCE", "COD", "RECHARGE", "ALIPAY", "WECHAT_PAY");
    }

    /**
     * 断言枚举取值与约束白名单**双向相等**。
     */
    private void assertWhitelist(String table, String constraint, Class<? extends Enum<?>> type) {
        Set<String> allowed = literalsOf(constraintDef(table, constraint));
        Set<String> declared = Arrays.stream(type.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toSet());
        assertThat(allowed)
                .as("%s 与 %s.%s 必须是同一份白名单", type.getSimpleName(), table, constraint)
                .containsExactlyInAnyOrderElementsOf(declared);
    }

    // ------------------------------------------------------------------
    // 阶段边界：财务菜单/权限只发布「已经真实存在的能力」
    // ------------------------------------------------------------------

    /**
     * F1-1 与 F1-2 一条财务菜单都没种（那时没有 Controller）；F1-3A 交付第一条受保护端点
     * {@code POST /scm/finance/receipt/add}（V66 发布 1500 隐藏目录 + 1521），
     * F1-3B 交付第二条 {@code POST /scm/finance/payment/add}（V67 只补 1522，父目录已存在）。
     *
     * <p><b>为什么这条断言必须随阶段收紧而不是删掉</b>：F1-1 那轮一度把设计稿 §16 的 1500–1531
     * 全部种了下去，结果是「已授权的页面菜单指向不存在的 {@code .vue}」——
     * {@code src/router/index.ts} 的 {@code route.component = modules[relativePath]} 在文件缺失时
     * 得到 {@code undefined}，菜单点开是空白页，而构建 / 类型检查 / 后端测试全绿。
     * {@code visible_flag = false} 掩盖不了它：那只影响 {@code meta.hideInMenu}。
     * 反过来，提前种一条<b>没有端点使用的权限串</b>（如 F1-3 阶段的 {@code receipt:query}）
     * 是同一类错误的镜像：menu_id 一旦被真实库应用就不可回收。
     * 所以下面逐值钉的是「已发布集合 == 已有真实端点的能力集合」。
     */
    @Test
    @DisplayName("阶段边界：财务只发布 1500 隐藏目录 + 1521 收款登记 + 1522 付款登记，无任何页面菜单")
    void financePublishesOnlyTheReceiptAddCapability() {
        assertThat(jdbc.queryForList(
                "SELECT menu_id FROM t_menu WHERE menu_id BETWEEN 1500 AND 1599 ORDER BY menu_id", Long.class))
                .as("V67 之后财务段只允许这三行；新增一行必须同时带来一个真实端点或一个真实页面")
                .containsExactly(1500L, 1521L, 1522L);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 1500 AND 1599 AND menu_type = 2",
                Integer.class))
                .as("页面菜单（menu_type=2）随 F1-6 的 .vue 一起落库，本阶段一个都没有")
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 1500 AND 1599 AND component IS NOT NULL",
                Integer.class))
                .as("没有任何一行财务菜单可以声明组件路径")
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT visible_flag FROM t_menu WHERE menu_id = 1500", Boolean.class))
                .as("目录本身也要保持隐藏，侧栏出现入口就等于出现空目录")
                .isFalse();

        // 权限串按内容再查一遍：换号段种同样会造成「已授权但无任何端点使用它」。
        assertThat(jdbc.queryForList(
                "SELECT DISTINCT api_perms FROM t_menu WHERE api_perms LIKE 'scm:finance:%' ORDER BY api_perms",
                String.class))
                .as("query / reverse / write-off / export 一律还没发布")
                .containsExactly("scm:finance:payment:add", "scm:finance:receipt:add");
        // 四条种子约定之一：api_perms == web_perms，前端按钮与服务端鉴权读的是同一个串。
        // 作用域限制在财务段：底座原生菜单行本就允许两者不对称，全库断言会误伤。
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_menu "
                        + "WHERE menu_id BETWEEN 1500 AND 1599 AND api_perms IS DISTINCT FROM web_perms",
                Integer.class))
                .isZero();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                        + "WHERE m.menu_id BETWEEN 1500 AND 1599", Integer.class))
                .as("财务段三行菜单（1500 目录 + 1521 / 1522 能力）各授超管兜底与 SCM_FINANCE，共 3 × 2 行")
                .isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                        + "WHERE m.menu_id = 1521", Integer.class))
                .as("能力点 1521 恰好两条授权行，多一条就是多授了一个角色")
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                        + "WHERE m.menu_id = 1522", Integer.class))
                .as("能力点 1522 同样恰好两条授权行")
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                        + "JOIN t_role r ON r.role_id = rm.role_id "
                        + "WHERE m.menu_id = 1521 AND r.role_code = 'SCM_FINANCE'", Integer.class))
                .as("SCM_FINANCE 按 role_code 授权（V56 口径），不硬编码 role_id")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                        + "JOIN t_role r ON r.role_id = rm.role_id "
                        + "WHERE m.menu_id = 1522 AND r.role_code = 'SCM_FINANCE'", Integer.class))
                .isEqualTo(1);
    }

    /**
     * D-5 的边界在 F1-1 只体现为「没有新增任何范围放宽点」：V65 是纯 DDL，
     * 因此全库的 {@code *:scope:all:query} 仍恰好是 V55 种的五个维度。
     * 财务的全范围继续只能来自既有显式授权，等 F1-3…F1-6 开始种权限时这条依然成立。
     */
    @Test
    @DisplayName("D-5：本轮没有新增任何 *:scope:all:query 范围放宽权限点")
    void noNewScopeWideningPermission() {
        assertThat(jdbc.queryForList(
                "SELECT DISTINCT api_perms FROM t_menu WHERE api_perms LIKE '%:scope:all:query' ORDER BY api_perms",
                String.class))
                .containsExactly("scm:customer:scope:all:query", "scm:delivery:scope:all:query",
                        "scm:inventory:scope:all:query", "scm:order:scope:all:query",
                        "scm:purchase:scope:all:query");
    }

    // ------------------------------------------------------------------
    // 造数助手：只写 finance_* 表，全部随用例事务回滚
    // ------------------------------------------------------------------

    private long insertReceivable(String prefix, String sourceType, String entryType,
                                  Long originalId, String reason, BigDecimal amount) {
        long sourceId = 900_000L + (System.nanoTime() % 1_000_000L);
        jdbc.update("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id, customer_id, "
                        + "customer_name_snapshot, entry_type, original_receivable_id, amount, event_at, reason) "
                        + "VALUES (?, ?, ?, 900001, 900001, '客户', ?, ?, ?, now(), ?)",
                no(prefix), sourceType, sourceId, entryType, originalId, amount, reason);
        return jdbc.queryForObject("SELECT id FROM finance_receivable WHERE source_type = ? AND source_id = ?",
                Long.class, sourceType, sourceId);
    }

    private void insertPayable(String prefix, String sourceType, Long sourceId, String entryType, String reason) {
        jdbc.update("INSERT INTO finance_payable (payable_no, source_type, source_id, purchase_order_id, supplier_id, "
                        + "supplier_name_snapshot, entry_type, original_payable_id, amount, event_at, reason) "
                        + "VALUES (?, ?, ?, 900001, 900001, '供应商', ?, "
                        + "CASE WHEN ? = 'RED' THEN 900001 ELSE NULL END, 100.0000, now(), ?)",
                no(prefix), sourceType, sourceId, entryType, entryType, reason);
    }

    private long insertReceipt(String receiptNo, String entryType, Long reverseOfId, String reason,
                               String externalReference) {
        jdbc.update("INSERT INTO finance_receipt (receipt_no, customer_id, customer_name_snapshot, amount, method, "
                        + "received_at, entry_type, reverse_of_id, reason, external_reference) "
                        + "VALUES (?, 900001, '客户', 100.0000, 'CASH', now(), ?, ?, ?, ?)",
                receiptNo, entryType, reverseOfId, reason, externalReference);
        return jdbc.queryForObject("SELECT id FROM finance_receipt WHERE receipt_no = ?", Long.class, receiptNo);
    }
}
