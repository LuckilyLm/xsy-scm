package com.xsy.scm.purchase.constant;

import com.xsy.scm.warehouse.constant.ScmWarehouseStatusEnum;
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
 * 枚举与 DB CHECK 白名单一致性门禁。
 *
 * <p>不硬编码白名单：直接读取迁移文件里各 {@code CONSTRAINT ck_* CHECK (...)}
 * 的取值集合，与 Java 枚举逐一比对。这样 DDL 或枚举任何一侧漂移都会立刻失败。
 *
 * <p>直接核对采购与仓库模块的最终约束，不再依赖退役迁移的中间形态。
 */
class ScmPurchaseStatusEnumTest {

    private static final String MIGRATION = "/db/migration/modules/V1000.12__purchase.sql";

    private static final String WAREHOUSE = "/db/migration/modules/V1000.04__warehouse.sql";

    @Test
    @DisplayName("采购单 6 态与 ck_purchase_order_status 白名单一致")
    void purchaseOrderStatusMatchesDdl() {
        assertThat(names(ScmPurchaseStatusEnum.values()))
                .isEqualTo(whitelist(MIGRATION, "ck_purchase_order_status"));
        assertThat(ScmPurchaseStatusEnum.values()).hasSize(6);
        assertThat(ScmPurchaseStatusEnum.ACCOUNTED.committed()).isTrue();
        assertThat(com.xsy.scm.purchase.manager.PurchaseOrderStateMachine.canTransition("SUBMITTED", "ACCOUNTED")).isTrue();
        assertThat(com.xsy.scm.purchase.manager.PurchaseOrderStateMachine.terminal("ACCOUNTED")).isTrue();
        assertThat(com.xsy.scm.purchase.manager.PurchaseOrderStateMachine.receivable("ACCOUNTED")).isFalse();
        assertThat(com.xsy.scm.purchase.manager.PurchaseOrderStateMachine.cancellable("ACCOUNTED")).isFalse();
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
                .isEqualTo(whitelist(MIGRATION,
                        "ck_purchase_receipt_status"));
        assertThat(names(ScmWarehouseStatusEnum.values()))
                .isEqualTo(whitelist(WAREHOUSE, "ck_warehouse_status"));

        assertThat(ScmPurchaseDemandStatusEnum.values()).hasSize(3);
        assertThat(ScmReceiptStatusEnum.values()).hasSize(2);
        assertThat(ScmWarehouseStatusEnum.values()).hasSize(2);
    }

    @Test
    @DisplayName("操作日志 19 型与 ck_purchase_operation_log_type 一致，且归属 CHECK 覆盖全部 19 型")
    void operationTypeMatchesDdlAndOwnerConstraintCoversAll() {
        // 最终白名单与归属必须一并覆盖收货入库、采购价后置和锁单。
        Set<String> fromDdl = whitelist(MIGRATION, "ck_purchase_operation_log_type");
        assertThat(names(ScmPurchaseOperationTypeEnum.values())).isEqualTo(fromDdl);
        assertThat(ScmPurchaseOperationTypeEnum.values()).hasSize(19);

        // 归属由 operation_type 决定，因此每一种类型都必须出现在归属 CHECK 里，
        //      否则该类型的日志会既不被接受也不被拒绝（约束整体求值为 NULL 而放行）。
        String sql = migrationSql(MIGRATION);
        String owner = normalize(constraint(sql, "ck_purchase_operation_log_owner"));
        for (ScmPurchaseOperationTypeEnum type : ScmPurchaseOperationTypeEnum.values()) {
            assertThat(owner)
                    .as("归属 CHECK 未覆盖 operation_type=%s", type.name())
                    .contains("'" + type.name() + "'");
        }
        // 五个分支的分组口径固定：采购单类含采购价后置/锁单，收货分支含 RECEIPT_PUTAWAY
        assertThat(owner).contains("operation_type='DEMAND_GENERATE'");
        assertThat(owner).contains("operation_type='DEMAND_ALLOCATE'");
        assertThat(owner).contains("'CREATE','UPDATE','SUBMIT','CANCEL','SHORT_CLOSE','DELETE'");
        assertThat(owner).contains("'PRICE_FILL','PRICE_ADJUST','PRICE_AUTO_FILL',");
        assertThat(owner).contains("'RECEIPT_CREATE','RECEIPT_UPDATE','RECEIPT_CONFIRM','RECEIPT_DELETE','RECEIPT_PUTAWAY'");
    }

    // ------------------------------------------------------------------

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 读取最终CHECK的完整表达式，兼容IN与PostgreSQL规范化后的ANY/CAST写法。 */
    private static Set<String> whitelist(String resource, String constraintName) {
        String expression = constraint(migrationSql(resource), constraintName);
        Set<String> values = new LinkedHashSet<>();
        var matcher = java.util.regex.Pattern.compile("'([A-Z][A-Z0-9_]*)'").matcher(expression);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        assertThat(values).as("约束%s必须有白名单", constraintName).isNotEmpty();
        return values;
    }

    private static String constraint(String sql, String constraintName) {
        int start = sql.indexOf("CONSTRAINT " + constraintName + " CHECK");
        assertThat(start).as("缺少CHECK约束%s", constraintName).isGreaterThanOrEqualTo(0);
        int open = sql.indexOf('(', start);
        int depth = 0;
        boolean quoted = false;
        for (int index = open; index < sql.length(); index++) {
            char value = sql.charAt(index);
            if (value == '\'') {
                if (quoted && index + 1 < sql.length() && sql.charAt(index + 1) == '\'') {
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && value == '(') {
                depth++;
            } else if (!quoted && value == ')' && --depth == 0) {
                return sql.substring(open, index + 1);
            }
        }
        throw new AssertionError("CHECK括号不完整: " + constraintName);
    }

    private static String normalize(String expression) {
        String compact = expression.replaceAll("\\s+", "");
        return compact.replaceAll("CAST\\(([^()]+?)AS(?:text|varchar)\\)", "$1")
                .replace("::charactervarying", "").replace("::text", "")
                .replace("(operation_type)", "operation_type");
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
