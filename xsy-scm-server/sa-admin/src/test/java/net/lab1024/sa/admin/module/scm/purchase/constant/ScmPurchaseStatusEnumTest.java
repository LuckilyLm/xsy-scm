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
 * W5 枚举 ↔ DB CHECK 白名单一致性门禁（W5 Target Design §11.1；B1 扩展）。
 *
 * <p>**不硬编码白名单**：直接读取 migration 文件里各 {@code CONSTRAINT ck_* CHECK (... IN (...))}
 * 的取值集合，与 Java 枚举逐一比对。这样 DDL 或枚举任何一侧漂移都会立刻失败。
 *
 * <p>操作日志类型在 B1（V22）追加了 {@code RECEIPT_PUTAWAY}，因此该白名单与归属 CHECK 从
 * {@code V22__scm_receipt_putaway_warehouse.sql} 读取（V22 是 {@code ck_purchase_operation_log_type}
 * / {@code ck_purchase_operation_log_owner} 的最终形态），其余状态白名单仍读 V15。
 */
class ScmPurchaseStatusEnumTest {

    private static final String MIGRATION = "/db/migration/V15__scm_purchase.sql";

    private static final String V22_MIGRATION = "/db/migration/V22__scm_receipt_putaway_warehouse.sql";

    @Test
    @DisplayName("采购单 6 态与 ck_purchase_order_status 白名单一致")
    void purchaseOrderStatusMatchesDdl() {
        assertThat(names(ScmPurchaseStatusEnum.values()))
                .isEqualTo(whitelist(MIGRATION, "ck_purchase_order_status"));
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
                .isEqualTo(whitelist(MIGRATION, "ck_purchase_demand_status"));
        assertThat(names(ScmReceiptStatusEnum.values()))
                .isEqualTo(whitelist(MIGRATION, "ck_purchase_receipt_status"));
        assertThat(names(ScmWarehouseStatusEnum.values()))
                .isEqualTo(whitelist(MIGRATION, "ck_warehouse_status"));

        assertThat(ScmPurchaseDemandStatusEnum.values()).hasSize(3);
        assertThat(ScmReceiptStatusEnum.values()).hasSize(2);
        assertThat(ScmWarehouseStatusEnum.values()).hasSize(2);
    }

    @Test
    @DisplayName("操作日志 13 型与 ck_purchase_operation_log_type 一致，且归属 CHECK 覆盖全部 13 型")
    void operationTypeMatchesDdlAndOwnerConstraintCoversAll() {
        // B1 追加 RECEIPT_PUTAWAY 后，白名单与归属 CHECK 的最终形态在 V22。
        Set<String> fromDdl = whitelist(V22_MIGRATION, "ck_purchase_operation_log_type");
        assertThat(names(ScmPurchaseOperationTypeEnum.values())).isEqualTo(fromDdl);
        assertThat(ScmPurchaseOperationTypeEnum.values()).hasSize(13);

        // Q14：归属由 operation_type 决定，因此**每一种**类型都必须出现在归属 CHECK 里，
        //      否则该类型的日志会既不被接受也不被拒绝（约束整体求值为 NULL 而放行）。
        String sql = migrationSql(V22_MIGRATION);
        String owner = between(sql, "ck_purchase_operation_log_owner", "COMMENT ON COLUMN purchase_receipt");
        for (ScmPurchaseOperationTypeEnum type : ScmPurchaseOperationTypeEnum.values()) {
            assertThat(owner)
                    .as("归属 CHECK 未覆盖 operation_type=%s", type.name())
                    .contains("'" + type.name() + "'");
        }
        // 四个分支的分组必须与设计 §7.12 一致（B1 在收货分支追加 RECEIPT_PUTAWAY）
        assertThat(owner).contains("operation_type = 'DEMAND_GENERATE'");
        assertThat(owner).contains("operation_type = 'DEMAND_ALLOCATE'");
        assertThat(owner).contains("'CREATE','UPDATE','SUBMIT','CANCEL','SHORT_CLOSE','DELETE'");
        assertThat(owner).contains("'RECEIPT_CREATE','RECEIPT_UPDATE','RECEIPT_CONFIRM','RECEIPT_DELETE','RECEIPT_PUTAWAY'");
    }

    // ------------------------------------------------------------------

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 取出 {@code CONSTRAINT <name> ... IN ( ... )} 中的取值集合（保持书写顺序）。
     */
    private static Set<String> whitelist(String resource, String constraintName) {
        String sql = migrationSql(resource);
        int name = sql.indexOf(constraintName);
        assertThat(name).as("%s 缺少约束 %s", resource, constraintName).isGreaterThanOrEqualTo(0);
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
        assertThat(start).as("缺少 %s", from).isGreaterThanOrEqualTo(0);
        int end = sql.indexOf(to, start + from.length());
        assertThat(end).as("%s 之后缺少 %s", from, to).isGreaterThan(start);
        return sql.substring(start, end);
    }

    private static String migrationSql(String resource) {
        try (InputStream in = ScmPurchaseStatusEnumTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("classpath 上找不到 %s", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取 " + resource + " 失败", e);
        }
    }
}
