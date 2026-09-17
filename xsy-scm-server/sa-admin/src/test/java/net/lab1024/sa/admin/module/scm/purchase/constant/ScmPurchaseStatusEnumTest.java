package net.lab1024.sa.admin.module.scm.purchase.constant;

import net.lab1024.sa.admin.module.scm.warehouse.constant.ScmWarehouseStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W5 枚举 ↔ DB CHECK 白名单一致性门禁（W5 Target Design §11.1）。
 *
 * <p>**不硬编码白名单**：直接读取 {@code db/migration/V15__scm_purchase.sql} 里
 * 各 {@code CONSTRAINT ck_* CHECK (... IN (...))} 的取值集合，与 Java 枚举逐一比对。
 * 这样 DDL 或枚举任何一侧漂移都会立刻失败，而不是靠人工记忆同步。
 */
class ScmPurchaseStatusEnumTest {

    private static final String MIGRATION = "/db/migration/V15__scm_purchase.sql";

    @Test
    @DisplayName("采购单 6 态与 ck_purchase_order_status 白名单一致")
    void purchaseOrderStatusMatchesDdl() {
        assertThat(names(ScmPurchaseStatusEnum.values()))
                .isEqualTo(whitelist("ck_purchase_order_status"));
        assertThat(ScmPurchaseStatusEnum.values()).hasSize(6);
        // 终态判定：RECEIVED / SHORT_CLOSED / CANCELLED 不再有任何后继
        assertThat(ScmPurchaseStatusEnum.RECEIVED).isNotNull();
        assertThat(ScmPurchaseStatusEnum.SHORT_CLOSED).isNotNull();
        assertThat(ScmPurchaseStatusEnum.CANCELLED).isNotNull();
    }

    @Test
    @DisplayName("需求 3 态 / 收货 2 态 / 仓库 2 态与各自白名单一致")
    void demandReceiptWarehouseStatusMatchDdl() {
        assertThat(names(ScmPurchaseDemandStatusEnum.values()))
                .isEqualTo(whitelist("ck_purchase_demand_status"));
        assertThat(names(ScmReceiptStatusEnum.values()))
                .isEqualTo(whitelist("ck_purchase_receipt_status"));
        assertThat(names(ScmWarehouseStatusEnum.values()))
                .isEqualTo(whitelist("ck_warehouse_status"));

        assertThat(ScmPurchaseDemandStatusEnum.values()).hasSize(3);
        assertThat(ScmReceiptStatusEnum.values()).hasSize(2);
        assertThat(ScmWarehouseStatusEnum.values()).hasSize(2);
    }

    @Test
    @DisplayName("操作日志 12 型与 ck_purchase_operation_log_type 一致，且归属 CHECK 覆盖全部 12 型")
    void operationTypeMatchesDdlAndOwnerConstraintCoversAll() {
        Set<String> fromDdl = whitelist("ck_purchase_operation_log_type");
        assertThat(names(ScmPurchaseOperationTypeEnum.values())).isEqualTo(fromDdl);
        assertThat(ScmPurchaseOperationTypeEnum.values()).hasSize(12);

        // Q14：归属由 operation_type 决定，因此**每一种**类型都必须出现在归属 CHECK 里，
        //      否则该类型的日志会既不被接受也不被拒绝（约束整体求值为 NULL 而放行）。
        String sql = migrationSql();
        String owner = between(sql, "ck_purchase_operation_log_owner", "ck_purchase_operation_log_before");
        for (ScmPurchaseOperationTypeEnum type : ScmPurchaseOperationTypeEnum.values()) {
            assertThat(owner)
                    .as("归属 CHECK 未覆盖 operation_type=%s", type.name())
                    .contains("'" + type.name() + "'");
        }
        // 四个分支的分组必须与设计 §7.12 一致
        assertThat(owner).contains("operation_type = 'DEMAND_GENERATE'");
        assertThat(owner).contains("operation_type = 'DEMAND_ALLOCATE'");
        assertThat(owner).contains("'CREATE','UPDATE','SUBMIT','CANCEL','SHORT_CLOSE','DELETE'");
        assertThat(owner).contains("'RECEIPT_CREATE','RECEIPT_UPDATE','RECEIPT_CONFIRM','RECEIPT_DELETE'");
    }

    // ------------------------------------------------------------------

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 取出 {@code CONSTRAINT <name> ... IN ( ... )} 中的取值集合（保持书写顺序）。 */
    private static Set<String> whitelist(String constraintName) {
        String sql = migrationSql();
        int name = sql.indexOf(constraintName);
        assertThat(name).as("V15 缺少约束 %s", constraintName).isGreaterThanOrEqualTo(0);
        int open = sql.indexOf("IN (", name);
        assertThat(open).as("约束 %s 不是 IN 白名单形式", constraintName).isGreaterThanOrEqualTo(0);
        int close = sql.indexOf(')', open);
        assertThat(close).isGreaterThan(open);
        return Arrays.stream(sql.substring(open + "IN (".length(), close).split(","))
                .map(String::trim)
                .map(s -> s.replace("'", ""))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String between(String sql, String from, String to) {
        int start = sql.indexOf(from);
        assertThat(start).as("V15 缺少 %s", from).isGreaterThanOrEqualTo(0);
        int end = sql.indexOf(to, start + from.length());
        assertThat(end).as("V15 中 %s 之后缺少 %s", from, to).isGreaterThan(start);
        return sql.substring(start, end);
    }

    private static String migrationSql() {
        try (InputStream in = ScmPurchaseStatusEnumTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(in).as("classpath 上找不到 %s", MIGRATION).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取 " + MIGRATION + " 失败", e);
        }
    }
}
