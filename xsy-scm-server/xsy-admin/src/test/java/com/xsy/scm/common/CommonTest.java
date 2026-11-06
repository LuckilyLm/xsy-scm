package com.xsy.scm.common;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.admin.XsyApplication;
import com.xsy.scm.admin.module.system.employee.dao.EmployeeDao;
import com.xsy.scm.admin.module.system.employee.domain.entity.EmployeeEntity;
import com.xsy.scm.admin.test.PgITBaselineDatabase;
import com.xsy.scm.admin.test.PgITDatabase;
import com.xsy.scm.admin.test.PgITMigrationHistory;
import com.xsy.scm.admin.test.PgITPaths;
import com.xsy.scm.base.common.json.serializer.BigDecimalNullZeroSerializer;
import com.xsy.scm.base.config.MybatisPlusConfig;
import com.xsy.scm.base.module.support.job.core.XsyJob;
import com.xsy.scm.common.dao.AppendOnlyMapper;
import com.xsy.scm.common.excel.ScmExcel;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.export.ScmExportColumnSelection.Column;
import com.xsy.scm.common.export.ScmExportColumnSelection;
import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog.Condition;
import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog.Field;
import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.validation.ScmServiceValidationAspect;
import com.xsy.scm.customer.dao.CustomerDao;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.permission.CustomerPermission;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.finance.dao.FinancePayableDao;
import com.xsy.scm.inventory.dao.InventoryBalanceDao;
import com.xsy.scm.inventory.dao.InventoryMovementDao;
import com.xsy.scm.inventory.domain.entity.InventoryBalanceEntity;
import com.xsy.scm.inventory.service.InventoryBalanceQueryService;
import com.xsy.scm.inventory.service.InventoryCommandService;
import com.xsy.scm.inventory.service.InventoryMovementQueryService;
import com.xsy.scm.product.domain.form.ProductSkuForm;
import com.xsy.scm.product.domain.form.ProductSpuAddForm;
import com.xsy.scm.product.domain.form.ProductSpuQueryForm;
import com.xsy.scm.product.support.ProductAdvancedFilters;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandEntity;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.purchase.support.PurchaseInventoryContract;
import com.xsy.scm.report.domain.form.ScmPurchaseReportQueryForm;
import com.xsy.scm.report.domain.form.ScmSalesReportQueryForm;
import com.xsy.scm.report.support.ScmReportAdvancedFilters;
import com.xsy.scm.supplier.dao.SupplierDao;
import com.xsy.scm.supplier.dao.SupplierSkuDao;
import com.xsy.scm.supplier.domain.entity.SupplierEntity;
import com.xsy.scm.supplier.domain.entity.SupplierSkuEntity;
import com.xsy.scm.supplier.domain.form.SupplierAddForm;
import com.xsy.scm.supplier.domain.form.SupplierSkuItemForm;
import com.xsy.scm.supplier.domain.form.SupplierSkuReplaceForm;
import com.xsy.scm.supplier.permission.SupplierPermission;
import com.xsy.scm.supplier.service.SupplierService;
import com.xsy.scm.supplier.service.SupplierSkuService;
import jakarta.validation.Validation;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.Data;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameResolver;
import com.xsy.scm.common.json.ScmOperatorSnapshotSerializer;
import com.xsy.scm.common.constant.ScmCustomerStatusEnum;
import com.xsy.scm.common.time.ScmDateTimeRange;
import com.xsy.scm.common.time.ScmDateTimeRangeResolver;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

class CommonTest {

    @Nested
    class CommonUnitTest {
        @Nested

        class AppendOnlyMapperTest {
            @Test
            @SuppressWarnings("unchecked")
            void standardMutationEntrypointsRejectBeforeAnySql() {
                AppendOnlyMapper<Object> mapper = mock(AppendOnlyMapper.class, CALLS_REAL_METHODS);
                Object fact = new Object();
                assertThatThrownBy(() -> mapper.deleteById((Serializable) 1L)).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> mapper.deleteById(fact)).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> mapper.deleteById(1L, true)).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> mapper.delete(null)).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> mapper.deleteByIds(List.of(1L), true)).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> mapper.updateById(fact)).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> mapper.update(fact, null)).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> mapper.updateById(List.of(fact), 50)).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> mapper.insertOrUpdate(fact)).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> mapper.insertOrUpdate(List.of(fact), 50)).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> mapper.insertOrUpdate(List.of(fact), (session, row) -> true, 50))
                    .isInstanceOf(UnsupportedOperationException.class);
            }
        }

        @Nested

        class AppendOnlySqlContractTest {
            private static final Set<String> FACTS = Set.of(
                "xsy_inventory_movement", "xsy_inventory_lot_movement", "xsy_price_operation_log",
                "xsy_customer_balance_movement", "xsy_customer_promotion_scan", "xsy_order_bundle_component",
                "xsy_purchase_demand_calculation_batch_item", "xsy_purchase_inquiry_quote",
                "xsy_purchase_share_feedback", "xsy_purchase_account", "xsy_purchase_account_item",
                "xsy_purchase_cost_adjustment", "xsy_finance_purchase_cost_entry", "xsy_finance_customer_expense",
                "xsy_finance_customer_expense_void", "xsy_finance_receivable", "xsy_finance_receivable_item",
                "xsy_finance_payable", "xsy_finance_payable_item", "xsy_finance_receipt", "xsy_finance_payment",
                "xsy_finance_write_off", "xsy_finance_waiver", "xsy_finance_operation_log",
                "xsy_payment_refund_recovery_log", "xsy_mall_home_release", "xsy_trace_event",
                "xsy_trace_unit_binding", "xsy_trace_unit_unbind", "xsy_print_record",
                "xsy_report_negative_stock_snapshot");
            private static final Map<String, Set<String>> SNAPSHOTS = Map.of(
                "xsy_order_discount", Set.of("sales_order_id", "discount_amount", "special_discount_amount", "allocations", "activity_snapshot", "coupon_snapshot"),
                "xsy_order_promotion_gift", Set.of("sales_order_id", "activity_id", "activity_version", "sku_id", "quantity", "rule_snapshot"),
                "xsy_delivery_plan_proposal", Set.of("route_id", "input_snapshot", "result_snapshot", "provider_code", "provider_version", "estimated_flag", "rule_code", "stop_count", "total_distance", "created_at", "created_by"));

            @Test
            void customStatementsDoNotMutateFactsOrFrozenFields() throws Exception {
                var factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/scm/**/*.xml");
                assertThat(resources).isNotEmpty();
                Pattern mutation = Pattern.compile("(?is)\\b(?:update|delete\\s+from)\\s+(?:xsy\\.)?(xsy_[a-z_]+)\\b");
                for (var resource : resources) {
                    try (var input = resource.getInputStream()) {
                        var document = factory.newDocumentBuilder().parse(input);
                        var nodes = document.getDocumentElement().getChildNodes();
                        for (int i = 0; i < nodes.getLength(); i++) {
                            var node = nodes.item(i);
                            if (!Set.of("insert", "update", "delete").contains(node.getNodeName())) {
                                continue;
                            }
                            String sql = node.getTextContent().toLowerCase(java.util.Locale.ROOT);
                            var matches = mutation.matcher(sql);
                            while (matches.find()) {
                                String table = matches.group(1);
                                assertThat(FACTS).as("%s 不得覆盖或删除记录表 %s", resource.getFilename(), table).doesNotContain(table);
                                if (SNAPSHOTS.containsKey(table) && sql.contains("set")) {
                                    String assignments = sql.substring(sql.indexOf("set") + 3).split("\\bwhere\\b", 2)[0];
                                    for (String field : SNAPSHOTS.get(table)) {
                                        assertThat(Pattern.compile("\\b" + field + "\\s*=").matcher(assignments).find())
                                            .as("%s 不得重写保存字段 %s.%s", resource.getFilename(), table, field).isFalse();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }



        @Nested

        class ScmAdvancedFilterCatalogTest {
            private static final List<Field> FIELDS = List.of(
                    new Field("categoryId", "分类编号", "EQ", "ID"),
                    new Field("keyword", "商品关键字", "CONTAINS", "TEXT"),
                    new Field("hasBarcode", "有条码", "EQ", "BOOLEAN"));

            @Test
            void onlyKnownFieldsAndSupportedOperatorsCanBindToExistingForm() {
                var values = ScmAdvancedFilterCatalog.resolve(List.of(
                        new Condition("categoryId", "EQ", "1234567890123456"),
                        new Condition("keyword", "CONTAINS", "青菜")), FIELDS);
                assertThat(ScmAdvancedFilterCatalog.id(values, "categoryId", null)).isEqualTo(1234567890123456L);
                assertThat(ScmAdvancedFilterCatalog.text(values, "keyword", null)).isEqualTo("青菜");
                assertThatThrownBy(() -> ScmAdvancedFilterCatalog.resolve(
                        List.of(new Condition("keyword", "EQ", "青菜")), FIELDS))
                        .isInstanceOf(ScmBusinessException.class);
                assertThatThrownBy(() -> ScmAdvancedFilterCatalog.resolve(
                        List.of(new Condition("secret", "EQ", "1")), FIELDS))
                        .isInstanceOf(ScmBusinessException.class);
                assertThatThrownBy(() -> ScmAdvancedFilterCatalog.resolve(
                        List.of(new Condition("categoryId", "EQ", "1 OR 1=1")), FIELDS))
                        .isInstanceOf(ScmBusinessException.class);
                assertThatThrownBy(() -> ScmAdvancedFilterCatalog.resolve(
                        List.of(new Condition("hasBarcode", "EQ", "yes")), FIELDS))
                        .isInstanceOf(ScmBusinessException.class);
            }

            @Test
            void duplicateAndConflictingExistingConditionsFailClosed() {
                assertThatThrownBy(() -> ScmAdvancedFilterCatalog.resolve(List.of(
                        new Condition("keyword", "CONTAINS", "青菜"),
                        new Condition("keyword", "CONTAINS", "白菜")), FIELDS))
                        .isInstanceOf(ScmBusinessException.class);
                var values = ScmAdvancedFilterCatalog.resolve(
                        List.of(new Condition("categoryId", "EQ", "7")), FIELDS);
                assertThatThrownBy(() -> ScmAdvancedFilterCatalog.id(values, "categoryId", 8L))
                        .isInstanceOf(ScmBusinessException.class);
                assertThat(ScmAdvancedFilterCatalog.id(values, "categoryId", 7L)).isEqualTo(7L);
            }

            @Test
            void domainAdaptersReuseOfficialQueryFilters() {
                var product = new ProductSpuQueryForm();
                product.setAdvancedFilters(List.of(new Condition("hasBarcode", "EQ", "false")));
                ProductAdvancedFilters.apply(product);
                assertThat(product.getHasBarcode()).isFalse();

                var sales = new ScmSalesReportQueryForm();
                sales.setAdvancedFilters(List.of(new Condition("sellerId", "EQ", "27")));
                ScmReportAdvancedFilters.salesOrder(sales);
                assertThat(sales.getSellerId()).isEqualTo(27L);

                var purchase = new ScmPurchaseReportQueryForm();
                purchase.setAdvancedFilters(List.of(new Condition("warehouseId", "EQ", "13")));
                ScmReportAdvancedFilters.purchaseProduct(purchase);
                assertThat(purchase.getWarehouseId()).isEqualTo(13L);
            }
        }

        @Nested
        @EnabledIfEnvironmentVariable(named = "XSY_RULES_UPGRADE_DB_URL", matches = ".+")
        class ScmBackendRulesUpgradePgIT {
            @Test
            void upgradePreservesEveryBusinessRowAndSequence() throws Exception {
                String url = System.getenv("XSY_RULES_UPGRADE_DB_URL");
                // 只能连接专用副本，不允许以正式库或普通业务回归库执行该迁移演练。
                assertThat(url).matches("jdbc:postgresql://[^/]+/[a-zA-Z0-9_]+_upgrade_it(?:\\?.*)?");
                Flyway flyway = Flyway.configure().dataSource(url, PgITDatabase.user(), PgITDatabase.password())
                    .schemas("xsy").defaultSchema("xsy").cleanDisabled(true).placeholderReplacement(false)
                    .locations("classpath:db/migration/modules").load();
                assertThat(flyway.info().current().getVersion().toString()).isEqualTo("1000.24");
                try (var connection = DriverManager.getConnection(url, PgITDatabase.user(), PgITDatabase.password())) {
                    Map<String, String> before = snapshot(connection);
                    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
                    flyway.validate();
                    assertThat(flyway.info().current().getVersion().toString()).isEqualTo("1001");
                    assertThat(snapshot(connection)).isEqualTo(before);
                    assertThat(flyway.migrate().migrationsExecuted).isZero();
                    assertThat(snapshot(connection)).isEqualTo(before);
                }
            }

            private Map<String, String> snapshot(Connection connection) throws Exception {
                Map<String, String> result = new LinkedHashMap<>();
                try (var query = connection.createStatement(); var rows = query.executeQuery(
                    "SELECT tablename FROM pg_tables WHERE schemaname='xsy' "
                        + "AND tablename <> 'flyway_schema_history' ORDER BY tablename")) {
                    while (rows.next()) {
                        String table = rows.getString(1);
                        try (var statement = connection.createStatement(); var digest = statement.executeQuery(
                            "SELECT count(*) || ':' || coalesce(md5(string_agg(d, '' ORDER BY d)), '') FROM "
                                + "(SELECT md5(to_jsonb(t)::text) d FROM xsy.\"" + table + "\" t) s")) {
                            digest.next();
                            result.put("table:" + table, digest.getString(1));
                        }
                    }
                }
                try (var query = connection.createStatement(); var rows = query.executeQuery(
                    "SELECT sequencename FROM pg_sequences WHERE schemaname='xsy' ORDER BY sequencename")) {
                    while (rows.next()) {
                        String sequence = rows.getString(1);
                        try (var statement = connection.createStatement(); var state = statement.executeQuery(
                            "SELECT last_value || ':' || is_called FROM xsy.\"" + sequence + "\"")) {
                            state.next();
                            result.put("sequence:" + sequence, state.getString(1));
                        }
                    }
                }
                return result;
            }
        }

        @Nested

        class ScmCustomerSupplierMigrationIT {
            private static final String URL = PgITBaselineDatabase.url();

            private static String user() {
                return PgITDatabase.user();
            }

            private static String password() {
                return PgITDatabase.password();
            }

            private static long scalar(Statement statement, String sql) throws SQLException {
                try (ResultSet result = statement.executeQuery(sql)) {
                    result.next();
                    return result.getLong(1);
                }
            }

            private static List<String> values(Statement statement, String sql) throws SQLException {
                List<String> values = new ArrayList<>();
                try (ResultSet result = statement.executeQuery(sql)) {
                    while (result.next()) {
                        values.add(result.getString(1));
                    }
                }
                return values;
            }

            @Test
            void migrationsApplyValidateAndExposeTheW2Aggregate() throws Exception {
                Flyway flyway = Flyway.configure().dataSource(URL, user(), password())
                        .schemas("xsy").defaultSchema("xsy").cleanDisabled(true)
                        .placeholderReplacement(false)
                        .locations("classpath:db/migration/modules").target("1000.24").load();
                flyway.migrate();
                flyway.validate();

                try (Connection connection = DriverManager.getConnection(URL, user(), password());
                     Statement statement = connection.createStatement()) {

                    PgITMigrationHistory.assertCovered(statement, "6", "7", "8", "9");

                    // ---- 2. 表与约束 ----
                    assertThat(scalar(statement,
                            "SELECT count(*) FROM information_schema.tables WHERE table_schema='xsy' "
                                    + "AND table_name IN ('xsy_customer','xsy_supplier','xsy_supplier_sku')"))
                            .as("W2 三张表（客户类型表已由 V231 下线）").isEqualTo(3);
                    assertThat(scalar(statement,
                            "SELECT count(*) FROM pg_constraint WHERE connamespace = 'xsy'::regnamespace "
                                    + "AND conname IN ("
                                    + "'ck_customer_credit_period','ck_customer_self_parent','ck_customer_status',"
                                    + "'ck_customer_settle_mode','ck_supplier_status','ck_supplier_sku_spec_values')"))
                            .as("关键 CHECK 约束").isEqualTo(6);

                    // partial unique：活动编码唯一，软删后可复用
                    assertThat(scalar(statement,
                            "SELECT count(*) FROM pg_indexes WHERE schemaname='xsy' AND indexname IN ("
                                    + "'uk_customer_code_active','uk_supplier_code_active','uk_supplier_sku_active')"))
                            .as("三条 partial unique index").isEqualTo(3);

                    // ---- 3. 反陷阱：绝不存在「每个供应商只能有一条默认」的唯一索引 ----
                    assertThat(scalar(statement,
                            "SELECT count(*) FROM pg_indexes WHERE schemaname='xsy' AND tablename='xsy_supplier_sku' "
                                    + "AND indexdef ILIKE '%is_default%'"))
                            .as("R12：不得为 is_default 建任何唯一约束或索引").isZero();

                    // ---- 4. V231：客户类型相关结构已下线 ----
                    assertThat(scalar(statement,
                            "SELECT count(*) FROM information_schema.tables WHERE table_schema='xsy' "
                                    + "AND table_name IN ('xsy_customer_type','xsy_customer_type_price',"
                                    + "'xsy_customer_price_batch_audit')"))
                            .as("客户类型 / 类型价 / 类型价批量审计表都必须被删除").isZero();
                    assertThat(scalar(statement,
                            "SELECT count(*) FROM information_schema.columns WHERE table_schema='xsy' "
                                    + "AND table_name='xsy_customer' AND column_name='customer_type_id'"))
                            .as("客户表不得再保留 customer_type_id").isZero();

                    // ---- 5. 菜单与权限点 ----
                    String activeIds = "431,432,434,441,442,443,444,445,461,462,463,464,471,472,473,474,475,481,482";
                    // 当前初始化不保存已下线类型菜单的软删占位；已有库仍可保留这些历史行。
                    assertThat(values(statement, "SELECT menu_id::text FROM t_menu WHERE NOT deleted_flag AND menu_id IN ("
                            + activeIds + ") ORDER BY menu_id"))
                            .as("现行客户/供应商菜单和权限必须逐项齐全")
                            .containsExactly("431", "432", "434", "441", "442", "443", "444", "445", "461", "462",
                                    "463", "464", "471", "472", "473", "474", "475", "481", "482");
                    assertThat(scalar(statement,
                            "SELECT count(*) FROM t_menu WHERE menu_id IN (433,451,452,453,454) AND NOT deleted_flag"))
                            .as("已下线客户类型菜单与权限不得重新启用").isZero();
                    assertThat(scalar(statement,
                            "SELECT count(*) FROM t_menu WHERE menu_id IN (434,463) AND visible_flag = FALSE AND NOT deleted_flag"))
                            .as("客户详情 / 供应商详情是隐藏但可深链的路由").isEqualTo(2);
                    assertThat(values(statement, "SELECT component FROM t_menu WHERE NOT deleted_flag AND component IS NOT NULL "
                            + "AND menu_id IN (" + activeIds + ")"))
                            .as("当前页面组件路径必须逐项对应正式页面")
                            .containsExactlyInAnyOrder("/business/scm/customer/customer-list.vue",
                                    "/business/scm/customer/customer-detail.vue", "/business/scm/supplier/supplier-list.vue",
                                    "/business/scm/supplier/supplier-detail.vue", "/business/scm/supplier/supplier-sku-list.vue");
                    assertThat(values(statement, "SELECT api_perms FROM t_menu WHERE NOT deleted_flag AND api_perms IS NOT NULL "
                            + "AND menu_id IN (" + activeIds + ")"))
                            .as("正式CRUD与供应商品目权限不得遗漏或写错")
                            .containsExactlyInAnyOrder(CustomerPermission.QUERY, CustomerPermission.ADD, CustomerPermission.UPDATE,
                                    CustomerPermission.STATUS, CustomerPermission.DELETE, SupplierPermission.QUERY,
                                    SupplierPermission.ADD, SupplierPermission.UPDATE, SupplierPermission.STATUS,
                                    SupplierPermission.DELETE, SupplierPermission.SKU_QUERY, SupplierPermission.SKU_UPDATE);
                    assertThat(scalar(statement,
                            "SELECT count(*) FROM t_role_menu WHERE role_id = 1 AND menu_id IN (431,432,433,434,441,442,443,444,445,451,452,453,454,461,462,463,464,471,472,473,474,475,481,482)"))
                            .as("管理员角色授权（客户类型菜单 433/451-454 已由 V231 撤回）").isEqualTo(19);
                    assertThat(scalar(statement,
                            "SELECT count(*) FROM t_role_menu WHERE menu_id IN (433,451,452,453,454)"))
                            .as("任何角色都不得残留已撤回类型权限").isZero();

                    // 菜单 id 序列已校正，后续迁移不会撞主键
                    assertThat(scalar(statement,
                            "SELECT last_value FROM t_menu_menu_id_seq"))
                            .as("setval 已把 menu_id 序列推到 max+1 之上").isGreaterThanOrEqualTo(483L);
                }
            }
        }

        @Nested

        class ScmExcelNumericTest {
            @Test
            void normalAmountsAreNumericAndCanBeSummed() throws Exception {
                var titles = List.of("单号", "金额");
                var response = new MockHttpServletResponse();
                ScmExcel.write(response, "金额.xlsx", "金额", titles, List.of(
                    ScmExcel.row(titles, "123456789012345678", new BigDecimal("0.1000")),
                    ScmExcel.row(titles, "123456789012345679", new BigDecimal("0.2000"))));
                try (var book = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
                    var sheet = book.getSheetAt(0);
                    assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("123456789012345678");
                    assertThat(sheet.getRow(1).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
                    var sum = sheet.createRow(3).createCell(1);
                    sum.setCellFormula("SUM(B2:B3)");
                    book.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(sum);
                    assertThat(BigDecimal.valueOf(sum.getNumericCellValue()).setScale(4, java.math.RoundingMode.HALF_UP))
                        .isEqualByComparingTo("0.3000");
                    assertThatThrownBy(() -> ScmExcel.setNumber(sum, new BigDecimal("99999999999999.9999")))
                        .isInstanceOf(ScmBusinessException.class).hasMessageContaining("15位有效数字");
                }
                assertThat(ScmExcel.row(List.of("金额"), new BigDecimal("99999999999999.9999")))
                    .containsExactly("99999999999999.9999");
            }
        }

        @Nested

        class ScmExportColumnSelectionTest {
            private static final List<Column> SCHEMA = List.of(
                    new Column("name", "名称"), new Column("quantity", "数量"),
                    new Column("cost", "成本", true));

            @Test
            void selectionCanReorderWhitelistedColumnsWithoutChangingDataSource() {
                var selected = ScmExportColumnSelection.resolve(List.of("quantity", "name"), SCHEMA, false);
                assertThat(selected.titles()).containsExactly("数量", "名称");
                assertThat(selected.project(List.of(List.of("小白菜", "10.0000", "未知"))))
                        .containsExactly(List.of("10.0000", "小白菜"));
            }

            @Test
            void unauthorizedCostAndUnknownOrDuplicateColumnsFailClosed() {
                assertThatThrownBy(() -> ScmExportColumnSelection.resolve(List.of("cost"), SCHEMA, false))
                        .isInstanceOf(ScmDataScopeException.class);
                assertThatThrownBy(() -> ScmExportColumnSelection.resolve(List.of("password"), SCHEMA, true))
                        .isInstanceOf(ScmBusinessException.class);
                assertThatThrownBy(() -> ScmExportColumnSelection.resolve(List.of("name", "name"), SCHEMA, true))
                        .isInstanceOf(ScmBusinessException.class);
                assertThatThrownBy(() -> ScmExportColumnSelection.resolve(List.of(), SCHEMA, true))
                        .isInstanceOf(ScmBusinessException.class);
            }

            @Test
            void legacyDefaultKeepsAllColumnsAndUnknownValuesRemainBlank() {
                var selected = ScmExportColumnSelection.resolve(null, SCHEMA, false);
                assertThat(selected.titles()).containsExactly("名称", "数量", "成本");
                assertThat(selected.project(List.of(java.util.Arrays.asList("小白菜", "10.0000", null))))
                        .containsExactly(List.of("小白菜", "10.0000", ""));
            }
        }

        @Nested

        class ScmOptimisticLockIT {
            @Data
            @TableName("w1_version_probe")
            public static class Probe {
                @TableId
                private Long id;
                private String name;
                @Version
                private Integer version;
            }

            public interface ProbeDao extends BaseMapper<Probe> {
            }

            @Test
            void staleUpdateIsRejectedAndPersistedVersionAdvances() throws Exception {
                PGSimpleDataSource source = new PGSimpleDataSource();
                source.setURL(PgITDatabase.url());
                source.setUser(PgITDatabase.user());
                source.setPassword(PgITDatabase.password());
                MybatisConfiguration config = new MybatisConfiguration();
                config.setEnvironment(new Environment("g1", new JdbcTransactionFactory(), source));
                config.addInterceptor(new MybatisPlusConfig().paginationInterceptor());
                config.addMapper(ProbeDao.class);
                try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(false)) {
                    try (var statement = session.getConnection().createStatement()) {
                        statement.execute("CREATE TEMP TABLE w1_version_probe (id bigint primary key, name text, version integer not null) ON COMMIT DROP");
                        statement.execute("INSERT INTO w1_version_probe VALUES (1, 'original', 0)");
                    }
                    var dao = session.getMapper(ProbeDao.class);
                    Probe first = dao.selectById(1L);
                    // Simulate a second reader, not the same first-level-cache object.
                    session.clearCache();
                    Probe stale = dao.selectById(1L);
                    assertThat(stale).isNotSameAs(first);
                    first.setName("first");
                    assertThat(dao.updateById(first)).isEqualTo(1);
                    stale.setName("stale");
                    assertThat(dao.updateById(stale)).as("stale version must not overwrite first update").isZero();
                    Probe saved = dao.selectById(1L);
                    assertThat(saved.getVersion()).isEqualTo(1);
                    assertThat(saved.getName()).isEqualTo("first");
                    session.rollback();
                }
            }
        }

        @Nested

        class ScmProductMigrationIT {
            @Test
            void migrationsApplyAndValidateWithoutChangingLegacySchema() throws Exception {
                String url = PgITDatabase.url();
                String user = PgITDatabase.user();
                String password = PgITDatabase.password();
                Flyway flyway = Flyway.configure().dataSource(url, user, password)
                        .schemas("xsy").defaultSchema("xsy").cleanDisabled(true)
                        .placeholderReplacement(false)
                        .locations("classpath:db/migration/modules").load();
                flyway.migrate();
                flyway.validate();
                try (var connection = DriverManager.getConnection(url, user, password);
                     var statement = connection.createStatement()) {
                    PgITMigrationHistory.assertCovered(statement, "6", "7");
                    try (var result = statement.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='xsy' AND table_name IN ('xsy_product_category','xsy_product_spu','xsy_product_sku','xsy_product_image')")) {
                        result.next();
                        assertThat(result.getInt(1)).isEqualTo(4);
                    }
                }
            }
        }

        @Nested

        class ScmServiceValidationAspectTest {
            @Test
            void invalidEnumAndAmountNeverReachBusinessCode() throws Throwable {
                try (var factory = Validation.buildDefaultValidatorFactory()) {
                    var aspect = new ScmServiceValidationAspect(factory.getValidator());
                    var invocation = mock(ProceedingJoinPoint.class);
                    CustomerAddForm form = new CustomerAddForm();
                    form.setName("客户");
                    form.setStatus("UNKNOWN");
                    when(invocation.getArgs()).thenReturn(new Object[]{form});
                    assertThatThrownBy(() -> aspect.validate(invocation)).isInstanceOf(ScmBusinessException.class)
                        .hasMessageContaining("客户状态无效");
                    form.setStatus(null);
                    form.setCreditPeriodValue(-1);
                    assertThatThrownBy(() -> aspect.validate(invocation)).isInstanceOf(ScmBusinessException.class)
                        .hasMessageContaining("账期值");
                    verify(invocation, never()).proceed();
                }
            }

            @Test
            void validFormAndOptionalNullContinueUnchanged() throws Throwable {
                try (var factory = Validation.buildDefaultValidatorFactory()) {
                    var aspect = new ScmServiceValidationAspect(factory.getValidator());
                    var invocation = mock(ProceedingJoinPoint.class);
                    CustomerAddForm form = new CustomerAddForm();
                    form.setName("客户");
                    when(invocation.getArgs()).thenReturn(new Object[]{form, null, "业务操作者"});
                    when(invocation.proceed()).thenReturn(123L);
                    assertThat(aspect.validate(invocation)).isEqualTo(123L);
                }
            }
        }
    }

    @Nested
    class CommonW2PgIT extends ScmW2PgITBase {
        @Nested
        @DisplayName("W2 乐观锁：客户、供应商与供应商规格在真实 PG + MP 拦截器上生效（PG IT）")
        class ScmCustomerSupplierOptimisticLockIT {
            @Autowired
            private CustomerDao customerDao;

            @Autowired
            private SupplierDao supplierDao;

            @Autowired
            private SupplierSkuDao supplierSkuDao;

            @Autowired
            private CustomerService customerService;

            @Autowired
            private SupplierService supplierService;

            @Autowired
            private SupplierSkuService supplierSkuService;

            private Long customerId() {
                CustomerAddForm form = new CustomerAddForm();
                form.setName("乐观锁客户");
                return customerService.add(form);
            }

            private Long supplierId() {
                SupplierAddForm form = new SupplierAddForm();
                form.setName("乐观锁供应商");
                return supplierService.add(form);
            }

            private Long supplierSkuRowId(Long supplierId, Long skuId) {
                SupplierSkuReplaceForm form = new SupplierSkuReplaceForm();
                form.setSupplierId(supplierId);
                SupplierSkuItemForm item = new SupplierSkuItemForm();
                item.setSkuId(skuId);
                item.setPurchaseUnit("kg");
                form.setItems(List.of(item));
                supplierSkuService.replace(form);
                return supplierSkuDao.selectActiveBySupplierId(supplierId).getFirst().getId();
            }

            /**
             * 断言数据库里的版本号已经被拦截器推进到期望值。
             */
            private void assertVersion(String table, Long id, int expected) {
                assertThat(jdbc.queryForObject("SELECT version FROM " + table + " WHERE id = ?", Integer.class, id))
                        .as(table + "：@Version 必须由拦截器推进").isEqualTo(expected);
            }

            @Test
            @DisplayName("customer：版本推进 + 落后版本更新 0 行 + Service 抛 40921")
            void customerVersionIsGuarded() {
                Long id = customerId();

                CustomerEntity entity = customerDao.selectById(id);
                assertThat(entity.getVersion()).isZero();
                entity.setName("第一次改名");
                assertThat(customerDao.updateById(entity)).isEqualTo(1);
                assertVersion("xsy_customer", id, 1);
                assertThat(entity.getVersion()).as("MP 会把新版本写回实体").isEqualTo(1);

                // 模拟「另一个会话已经改过」：把库里的版本推远
                jdbc.update("UPDATE xsy_customer SET version = version + 10 WHERE id = ?", id);
                entity.setName("落后版本的写入");
                assertThat(customerDao.updateById(entity)).as("落后版本必须更新 0 行").isZero();
                assertThat(jdbc.queryForObject("SELECT name FROM xsy_customer WHERE id = ?", String.class, id))
                        .as("失败写入不得写入数据库").isEqualTo("第一次改名");
            }

            @Test
            @DisplayName("supplier：版本推进 + 落后版本更新 0 行")
            void supplierVersionIsGuarded() {
                Long id = supplierId();

                SupplierEntity entity = supplierDao.selectById(id);
                assertThat(entity.getVersion()).isZero();
                entity.setName("第一次改名");
                assertThat(supplierDao.updateById(entity)).isEqualTo(1);
                assertVersion("xsy_supplier", id, 1);

                jdbc.update("UPDATE xsy_supplier SET version = version + 10 WHERE id = ?", id);
                entity.setName("落后版本的写入");
                assertThat(supplierDao.updateById(entity)).isZero();
                assertThat(jdbc.queryForObject("SELECT name FROM xsy_supplier WHERE id = ?", String.class, id))
                        .isEqualTo("第一次改名");
            }

            @Test
            @DisplayName("supplier_sku：版本推进 + 落后版本更新 0 行")
            void supplierSkuVersionIsGuarded() {
                Long supplierId = supplierId();
                Long skuId = newOnShelfSku("V1");
                Long rowId = supplierSkuRowId(supplierId, skuId);

                SupplierSkuEntity entity = supplierSkuDao.selectActiveBySupplierId(supplierId).getFirst();
                assertThat(entity.getVersion()).isZero();
                entity.setPurchaseUnit("件");
                assertThat(supplierSkuDao.updateById(entity)).isEqualTo(1);
                assertVersion("xsy_supplier_sku", rowId, 1);

                jdbc.update("UPDATE xsy_supplier_sku SET version = version + 10 WHERE id = ?", rowId);
                entity.setPurchaseUnit("箱");
                assertThat(supplierSkuDao.updateById(entity)).isZero();
                assertThat(jdbc.queryForObject("SELECT purchase_unit FROM xsy_supplier_sku WHERE id = ?", String.class, rowId))
                        .isEqualTo("件");
            }
        }

        @Nested

        class ScmMenuComponentExistencePgIT {
            /** 从测试工作目录向上找到仓库根（含 {@code xsy-scm-web/src/views} 的那一层）。 */
            private static Path webViewsRoot() {
                Path cursor = Path.of("").toAbsolutePath();
                for (int depth = 0; depth < 6 && cursor != null; depth++, cursor = cursor.getParent()) {
                    Path candidate = cursor.resolve("xsy-scm-web").resolve("src").resolve("views");
                    if (Files.isDirectory(candidate)) {
                        return candidate;
                    }
                }
                throw new IllegalStateException(
                        "未找到 xsy-scm-web/src/views：本用例必须在仓库检出内运行，否则菜单-组件一致性无法校验");
            }

            @Test
            @DisplayName("未删除的页面菜单都指向真实存在的 .vue 组件")
            void everyMenuComponentExistsOnDisk() {
                Path views = webViewsRoot();
                List<String> components = jdbc.queryForList(
                        "SELECT DISTINCT component FROM t_menu"
                                + " WHERE deleted_flag = FALSE AND component IS NOT NULL AND component <> ''"
                                + "   AND component NOT LIKE 'http%'"
                                + " ORDER BY component", String.class);

                List<String> missing = new ArrayList<>();
                for (String component : components) {
                    String relative = component.replace('\\', '/');
                    if (relative.startsWith("/")) {
                        relative = relative.substring(1);
                    }
                    String stem = relative.endsWith(".vue")
                            ? relative.substring(0, relative.length() - 4) : relative;
                    if (!Files.isRegularFile(views.resolve(stem + ".vue"))
                            && !Files.isRegularFile(views.resolve(stem).resolve("index.vue"))) {
                        missing.add(component);
                    }
                }

                assertThat(missing)
                        .as("以下页面菜单指向的组件文件不存在，点开必是空白页（构建/类型检查/后端测试都发现不了）")
                        .isEmpty();
                assertThat(components).as("扫描到的组件清单不应为空，否则本用例是空跑").isNotEmpty();
            }

            @Test
            @DisplayName("可见的一级与二级菜单都有图标，且图标名是 antd 的组件名")
            void everyVisibleMenuHasIcon() {
                List<String> withoutIcon = jdbc.queryForList(
                        "SELECT menu_id || ' ' || menu_name FROM t_menu"
                                + " WHERE deleted_flag = FALSE AND visible_flag = TRUE AND menu_type IN (1, 2)"
                                + "   AND (icon IS NULL OR btrim(icon) = '')"
                                + " ORDER BY parent_id, sort", String.class);

                // 菜单项不会为空图标留位：少一个图标，这一行的文字就比同级菜单左移一个图标位，
                // 展开目录时一组里缺几个图标、行首也对不齐。图标名存在库里，前端约定扫不到，只能在这拦。
                assertThat(withoutIcon)
                        .as("以下菜单没有图标，侧边栏里会比同级菜单少一格、文字错位")
                        .isEmpty();

                List<String> oddNames = jdbc.queryForList(
                        "SELECT menu_id || ' ' || menu_name || ' -> ' || icon FROM t_menu"
                                + " WHERE deleted_flag = FALSE AND icon IS NOT NULL AND btrim(icon) <> ''"
                                + "   AND icon !~ '^[A-Z][A-Za-z0-9]*(Outlined|Filled|TwoTone)$'"
                                + " ORDER BY menu_id", String.class);

                // 图标名对不上组件时页面不报错，只是这一项静默没有图标 —— 拼错一个字母就会中招。
                assertThat(oddNames)
                        .as("以下图标名不符合 antd 组件命名，前端解析不到，菜单会静默少一个图标")
                        .isEmpty();
            }
        }
    }

    @Nested
    class CommonW5PgIT extends ScmW5PgITBase {
        @Nested
        @DisplayName("P0-H 正式角色授权矩阵（PG IT）")
        class ScmBusinessRoleMatrixPgIT {
            private static final String COST_QUERY = "scm:report:cost:query";
            private static final String WAREHOUSE_ALL = "scm:inventory:scope:all:query";
            private static final String PURCHASE_ALL = "scm:purchase:scope:all:query";
            private static final String CUSTOMER_ALL = "scm:customer:scope:all:query";
            private static final String BALANCE_QUERY = "scm:inventory:balance:query";
            private static final String SCREEN_QUERY = "scm:screen:query";

            @Test
            @DisplayName("十个正式业务角色都存在，且授权矩阵非空")
            void formalRolesExistWithNonEmptyGrants() {
                List<String> roleCodes = jdbc.queryForList(
                        "SELECT role_code FROM t_role WHERE role_code LIKE 'SCM\\_%' ORDER BY role_code", String.class);
                assertThat(roleCodes).containsExactly("SCM_DISPATCHER", "SCM_DRIVER", "SCM_FINANCE", "SCM_PURCHASER",
                        "SCM_PURCHASER_LEAD", "SCM_SALES", "SCM_SALES_LEAD", "SCM_SORTER", "SCM_STOREKEEPER",
                        "SCM_STOREKEEPER_LEAD");
                // t_role 上根本没有 administrator_flag 这一列：超管位是员工的属性，不是角色的属性，
                // 所以「正式角色只能用 administrator_flag=false 的账号登录」这条只能由 E2E 保证。
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_schema = current_schema() AND table_name = 't_role'
                          AND column_name = 'administrator_flag'""", Integer.class)).isZero();
                assertThat(jdbc.queryForList("""
                        SELECT r.role_code FROM t_role r
                        WHERE r.role_code LIKE 'SCM\\_%'
                          AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id)
                        ORDER BY r.role_code""", String.class))
                        .as("没有任何正式角色是空壳")
                        .isEmpty();
            }

            @Test
            @DisplayName("配送 L3 矩阵：发车给调度与仓库主管，签收含司机，完成线路只给调度")
            void dispatchSignAndCompleteAreSplitAcrossRoles() {
                assertThat(holds("SCM_DISPATCHER", "scm:delivery:route:dispatch")).isTrue();
                assertThat(holds("SCM_STOREKEEPER_LEAD", "scm:delivery:route:dispatch")).isTrue();
                // 司机不持发车：发车会在库存域触发仓库范围守卫，而司机岗没有仓库授权。
                assertThat(holds("SCM_DRIVER", "scm:delivery:route:dispatch")).isFalse();
                // 司机持签收，同时读侧仍受 driverScope 收窄 —— 两条一起才等于「只能签自己在的线」。
                assertThat(holds("SCM_DRIVER", "scm:delivery:order:sign")).isTrue();
                assertThat(holds("SCM_DISPATCHER", "scm:delivery:order:sign")).isTrue();
                assertThat(holds("SCM_STOREKEEPER", "scm:delivery:order:sign")).isFalse();
                assertThat(holds("SCM_DISPATCHER", "scm:delivery:route:complete")).isTrue();
                assertThat(holds("SCM_DRIVER", "scm:delivery:route:complete")).isFalse();
                // 发车会改库存，因此不与「确认规划」同权，也不给销售岗：排线不等于有权发货。
                assertThat(holds("SCM_SALES", "scm:delivery:route:dispatch")).isFalse();
                assertThat(holds("SCM_SALES", "scm:delivery:order:sign")).isFalse();
            }

            @Test
            @DisplayName("分拣矩阵：分拣员只有干活的权限，队列管理权全部在仓库主管")
            void sortingRightsSplitBetweenSorterAndStorekeeperLead() {
                assertThat(holds("SCM_SORTER", "scm:sorting:task:query")).isTrue();
                assertThat(holds("SCM_SORTER", "scm:sorting:item:update")).isTrue();
                assertThat(holds("SCM_SORTER", "scm:sorting:task:complete")).isTrue();
                assertThat(holds("SCM_SORTER", "scm:sorting:task:print")).isTrue();
                // 任务页要展示与筛选仓库名，缺这条会在加载时 30005 —— 属于「页面必需」而不是顺带放宽。
                assertThat(holds("SCM_SORTER", "scm:warehouse:query")).isTrue();
                assertThat(holds("SCM_SORTER", "scm:sorting:task:add")).isFalse();
                assertThat(holds("SCM_SORTER", "scm:sorting:task:assign")).isFalse();
                assertThat(holds("SCM_SORTER", "scm:sorting:task:cancel")).isFalse();
                assertThat(holds("SCM_SORTER", "scm:sorting:task:reopen")).isFalse();
                assertThat(holds("SCM_SORTER", "scm:sorting:summary:query")).isFalse();

                for (String perm : List.of("scm:sorting:task:query", "scm:sorting:task:add", "scm:sorting:task:assign",
                        "scm:sorting:item:update", "scm:sorting:task:complete", "scm:sorting:task:cancel",
                        "scm:sorting:task:reopen", "scm:sorting:task:print", "scm:sorting:summary:query")) {
                    assertThat(holds("SCM_STOREKEEPER_LEAD", perm)).as("仓库主管拿到完整队列管理权：%s", perm).isTrue();
                }
                // 普通仓管员不在分拣岗位里：分拣走新增的 SCM_SORTER 角色，而不是给既有岗位顺带放行。
                assertThat(holds("SCM_STOREKEEPER", "scm:sorting:task:query")).isFalse();

                // 跨指派人可见性由 scm:sorting:task:assign 隐含：库里根本不该有第二条分拣范围权限。
                assertThat(jdbc.queryForList("SELECT web_perms FROM t_menu WHERE web_perms LIKE 'scm:sorting:scope%'",
                        String.class)).as("裁决补充第 15 条：不设 scm:sorting:scope:all:query").isEmpty();
                // 任何正式角色都不靠「角色名分支」放宽分拣范围：分拣维度只有仓库与员工两个。
                assertThat(jdbc.queryForList("""
                        SELECT DISTINCT m.web_perms FROM t_role_menu rm
                        JOIN t_menu m ON m.menu_id = rm.menu_id
                        JOIN t_role r ON r.role_id = rm.role_id
                        WHERE r.role_code = 'SCM_SORTER' AND m.web_perms LIKE '%scope:all%'""", String.class))
                        .as("分拣员不持任何维度的全量范围权限").isEmpty();
            }

            @Test
            @DisplayName("销售主管拿到退货/退款业务审批，普通销售员一点都没有")
            void salesLeadHoldsReturnAndRefundApprovalOnly() {
                assertThat(holds("SCM_SALES_LEAD", "scm:order:return:approve")).isTrue();
                assertThat(holds("SCM_SALES_LEAD", "scm:order:return:reject")).isTrue();
                assertThat(holds("SCM_SALES_LEAD", "scm:order:refund:complete")).isTrue();
                assertThat(holds("SCM_SALES", "scm:order:return:approve")).isFalse();
                assertThat(holds("SCM_SALES", "scm:order:refund:complete")).isFalse();
                // 「业务审批」不等于「资金操作」：本仓库现在没有任何付款/核销类权限点，
                // 销售主管因此也拿不到成本列 —— 退款付款必须另走财务权限。
                assertThat(holds("SCM_SALES_LEAD", COST_QUERY)).isFalse();
                assertThat(holds("SCM_SALES", COST_QUERY)).isFalse();
            }

            @Test
            @DisplayName("财务的全部仓库范围是一条显式授权权限，并且仍有独立成本权")
            void financeWidensWarehouseThroughScopePermissionOnly() {
                assertThat(holds("SCM_FINANCE", WAREHOUSE_ALL)).isTrue();
                assertThat(holds("SCM_FINANCE", COST_QUERY)).isTrue();
                assertThat(holds("SCM_FINANCE", CUSTOMER_ALL)).isTrue();
                // 财务不是「天生全量」：把这条权限收回，范围解析就回到授权行，不需要改任何业务代码
                assertThat(jdbc.queryForObject("SELECT count(*) FROM t_menu WHERE api_perms = ?", Integer.class,
                        WAREHOUSE_ALL)).isEqualTo(1);
            }

            @Test
            @DisplayName("库存数量权与库存成本权分离：采购员与仓管员都有数量权，都没有成本权与全仓范围")
            void quantityRightsAreSeparateFromCostRights() {
                assertThat(holds("SCM_PURCHASER", BALANCE_QUERY)).isTrue();
                assertThat(holds("SCM_PURCHASER", COST_QUERY)).isFalse();
                assertThat(holds("SCM_PURCHASER", WAREHOUSE_ALL)).isFalse();
                assertThat(holds("SCM_STOREKEEPER", BALANCE_QUERY)).isTrue();
                assertThat(holds("SCM_STOREKEEPER", COST_QUERY)).isFalse();
                // 成本权与全部仓库范围只在仓库主管与财务身上
                assertThat(holds("SCM_STOREKEEPER_LEAD", COST_QUERY)).isTrue();
                assertThat(holds("SCM_STOREKEEPER_LEAD", WAREHOUSE_ALL)).isTrue();
            }

            @Test
            @DisplayName("采购主管有全量采购范围但没有全仓范围：交集的另一条边界不会因归属而放宽")
            void purchaseLeadCannotBypassWarehouseDimension() {
                assertThat(holds("SCM_PURCHASER_LEAD", PURCHASE_ALL)).isTrue();
                assertThat(holds("SCM_PURCHASER_LEAD", WAREHOUSE_ALL)).isFalse();
                assertThat(holds("SCM_PURCHASER_LEAD", COST_QUERY)).isFalse();
                assertThat(holds("SCM_PURCHASER", PURCHASE_ALL)).isFalse();
            }

            @Test
            @DisplayName("数据大屏入口仍只授超管：范围已经接好，但授权决定单独留给人裁决")
            void screenPermissionStaysAdministratorOnly() {
                assertThat(holds("SCM_FINANCE", SCREEN_QUERY)).isFalse();
                assertThat(holds("SCM_STOREKEEPER_LEAD", SCREEN_QUERY)).isFalse();
                assertThat(holds("SCM_DISPATCHER", SCREEN_QUERY)).isFalse();
                // 角色表没有超管位这一列，所以「谁持有大屏」只能按角色编码判定：正式业务角色一个都没有
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM t_role_menu rm
                        JOIN t_menu m ON m.menu_id = rm.menu_id
                        JOIN t_role r ON r.role_id = rm.role_id
                        WHERE m.api_perms = ? AND r.role_code LIKE 'SCM\\_%'""", Integer.class, SCREEN_QUERY)).isZero();
            }

            /** 角色是否持有某个权限码（菜单上的 api_perms 与本仓库的 web_perms 同值，取 api_perms）。 */
            private boolean holds(String roleCode, String permission) {
                Integer count = jdbc.queryForObject("""
                        SELECT count(*) FROM t_role_menu rm
                        JOIN t_role r ON r.role_id = rm.role_id
                        JOIN t_menu m ON m.menu_id = rm.menu_id
                        WHERE r.role_code = ? AND m.api_perms = ?""", Integer.class, roleCode, permission);
                return count != null && count > 0;
            }
        }
    }

    @Nested
    class CommonW6PgIT extends ScmW6PgITBase {
        @Nested

        class ScmBackendRulesPgIT {
            @Autowired
            private CustomerService customers;
            @Autowired
            private FinancePayableDao payables;

            @Test
            void latestSchemaHasNoBusinessGuardsAndKeepsStructuralConstraints() {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace='xsy'::regnamespace AND contype='c'", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid WHERE c.relnamespace='xsy'::regnamespace AND NOT t.tgisinternal", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_proc WHERE pronamespace='xsy'::regnamespace AND prorettype='trigger'::regtype", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace='xsy'::regnamespace AND contype IN ('p','u')", Integer.class)).isGreaterThan(200);
                assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema='xsy' AND table_name='xsy_customer' AND column_name='settlement_customer_id'", String.class)).isEqualTo("NO");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='1001' AND success", Integer.class)).isEqualTo(1);
            }

            @Test
            void customerSelfSettlementAndInvalidEnumAreHandledBeforePersistence() {
                CustomerAddForm form = new CustomerAddForm();
                form.setName(prefix + "自结算");
                Long id = customers.add(form);
                assertThat(jdbc.queryForObject("SELECT settlement_customer_id FROM xsy_customer WHERE id=?", Long.class, id)).isEqualTo(id);
                form.setName(prefix + "非法状态");
                form.setStatus("BOGUS");
                assertThatThrownBy(() -> customers.add(form)).isInstanceOf(ScmBusinessException.class);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_customer WHERE name=?", Integer.class, form.getName())).isZero();
            }

            @Test
            void realMapperRejectsFactMutationWithoutDatabaseTriggers() {
                assertThatThrownBy(() -> inventoryMovementDao.deleteById((java.io.Serializable) Long.MAX_VALUE))
                    .isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> payables.deleteById((java.io.Serializable) Long.MAX_VALUE))
                    .isInstanceOf(UnsupportedOperationException.class);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid WHERE c.relnamespace='xsy'::regnamespace AND NOT t.tgisinternal", Integer.class)).isZero();
            }

            @Test
            void purchaseConfirmationFreezesPaymentPeriodWithoutTrigger() {
                Long sku = newOnShelfSku("rule");
                W6Fixture fixture = inboundFixture("rule", sku, "5.0000");
                jdbc.update("UPDATE xsy_supplier SET payment_period_days=17 WHERE id=?", fixture.supplierId());
                confirmReceipt(fixture.receipt().getId(), "5.0000");
                Map<String,Object> frozen = jdbc.queryForMap("SELECT id,due_date,payment_rule_snapshot->>'paymentPeriodDays' AS days,(event_at AT TIME ZONE 'Asia/Shanghai')::date AS business_date FROM xsy_finance_payable WHERE source_type='PURCHASE_RECEIPT' AND source_id=?", fixture.receipt().getId());
                assertThat(frozen.get("days")).isEqualTo("17");
                LocalDate business = ((java.sql.Date) frozen.get("business_date")).toLocalDate();
                assertThat(((java.sql.Date) frozen.get("due_date")).toLocalDate()).isEqualTo(business.plusDays(17));
                jdbc.update("UPDATE xsy_supplier SET payment_period_days=99 WHERE id=?", fixture.supplierId());
                assertThat(jdbc.queryForObject("SELECT payment_rule_snapshot->>'paymentPeriodDays' FROM xsy_finance_payable WHERE id=?", String.class, frozen.get("id"))).isEqualTo("17");
            }
        }

        @Nested

        class XsyNamespacePgIT {
            @Test
            void jobClassNamesResolveAfterMigration() throws Exception {
                var names = jdbc.queryForList("SELECT job_class FROM t_smart_job WHERE deleted_flag = false", String.class);
                assertThat(names).isNotEmpty();
                for (String name : names) {
                    assertThat(name).startsWith("com.xsy.scm.");
                    assertThat(XsyJob.class.isAssignableFrom(Class.forName(name))).as(name).isTrue();
                }
                assertThat(jdbc.queryForObject("SELECT count(*) FROM t_smart_job WHERE job_class LIKE ?", Integer.class,
                    "%module.support.job.sample.XsyJobSample%" )).isEqualTo(2);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM t_smart_job WHERE job_class=?", Integer.class,
                    "com.xsy.scm.admin.module.system.support.FileScratchCleanupJob")).isEqualTo(1);
            }

            @Test
            void singleScanRootIncludesEveryModule() {
                assertThat(XsyApplication.COMPONENT_SCAN).isEqualTo("com.xsy.scm");
                assertThat(XsyApplication.COMPONENT_SCAN_PATHS).containsExactly("com.xsy.scm");
            }
        }
    }

    @Nested
    class ScmCustomerStatusEnumTest {
        @Test
        @DisplayName("三态齐备，取值域与 DB CHECK 一致")
        void exposesExactlyThreeStates() {
            assertThat(ScmCustomerStatusEnum.values())
                    .extracting(Enum::name)
                    .containsExactlyInAnyOrder("COOPERATING", "SUSPENDED", "BLACKLIST");
        }

        @Test
        @DisplayName("只有 COOPERATING 可交易")
        void onlyCooperatingIsTradable() {
            assertThat(ScmCustomerStatusEnum.COOPERATING.tradable()).isTrue();
            assertThat(ScmCustomerStatusEnum.SUSPENDED.tradable()).isFalse();
            assertThat(ScmCustomerStatusEnum.BLACKLIST.tradable()).isFalse();
        }

        @Test
        @DisplayName("可交易状态恰好一个：防止后续顺手放宽成多个")
        void exactlyOneTradableState() {
            long tradable = java.util.Arrays.stream(ScmCustomerStatusEnum.values())
                    .filter(ScmCustomerStatusEnum::tradable)
                    .count();
            assertThat(tradable).isEqualTo(1L);
        }
    }

    @Nested
    class CommonUnitTest2 {
        @Nested

        class ScmBigDecimalNullSemanticsTest {
            /**
             * SCM 业务代码根包；任何新增 SCM 代码都必须落在这个包下。
             *
             * <p>SCM 生产代码整体位于 {@code com.xsy.scm}，旧包
             * {@code com.xsy.scm.admin.module.scm} 下已无任何生产类，因此扫描根必须跟着指向本包 ——
             * 继续指向旧包会扫到 0 个类，而下面 {@code isNotEmpty()} 断言会让它当场失败
             * （这正是该断言存在的意义）。
             */
            private static final String SCM_ROOT_PACKAGE = "com.xsy.scm";

            private final ObjectMapper objectMapper = new ObjectMapper();

            // ------------------------------------------------------------------
            // 测试夹具：模拟一条"未定价"与一条"价格为零"的商品行
            // ------------------------------------------------------------------

            /**
             * 模拟 SCM 价格视图对象。
             */
            static class ScmPriceFixture {

                /**
                 * SCM 约定：同时声明 {@code using} 与 {@code nullsUsing}。
                 * Jackson 的 {@code using} 不会作用于 null，必须靠 {@code nullsUsing} 显式兜住 null，
                 * 否则 null 的最终表现取决于全局 NullValueSerializer，容易在配置变动后失守。
                 */
                @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
                public BigDecimal unitPrice;

                public String priceStatus;

                ScmPriceFixture(BigDecimal unitPrice, String priceStatus) {
                    this.unitPrice = unitPrice;
                    this.priceStatus = priceStatus;
                }
            }

            // ------------------------------------------------------------------
            // 1. 序列化器约定
            // ------------------------------------------------------------------

            @Test
            @DisplayName("SCM 序列化器：null 保持为 null，绝不写成 0")
            void nullStaysNull() throws Exception {
                String json = objectMapper.writeValueAsString(new ScmPriceFixture(null, "UNPRICED"));

                assertThat(json).contains("\"unitPrice\":null");
                assertThat(json).doesNotContain("\"unitPrice\":0");
                assertThat(json).doesNotContain("\"unitPrice\":\"0.0000\"");
            }

            @Test
            @DisplayName("SCM 序列化器：真正的 0 元输出 \"0.0000\"，与 null 严格区分")
            void zeroIsZeroString() throws Exception {
                String json = objectMapper.writeValueAsString(new ScmPriceFixture(BigDecimal.ZERO, "PRICED"));

                assertThat(json).contains("\"unitPrice\":\"0.0000\"");
            }

            @Test
            @DisplayName("SCM 序列化器：null 与 0 的序列化结果必须不同")
            void nullAndZeroAreDistinguishable() throws Exception {
                String unpriced = objectMapper.writeValueAsString(new ScmPriceFixture(null, "UNPRICED"));
                String zeroPriced = objectMapper.writeValueAsString(new ScmPriceFixture(BigDecimal.ZERO, "PRICED"));

                assertThat(unpriced).isNotEqualTo(zeroPriced);
            }

            @Test
            @DisplayName("SCM 序列化器：金额按 4 位小数 HALF_UP 输出为字符串")
            void scaleAndRounding() throws Exception {
                assertThat(objectMapper.writeValueAsString(new ScmPriceFixture(new BigDecimal("12.34565"), "PRICED")))
                        .contains("\"unitPrice\":\"12.3457\"");
                assertThat(objectMapper.writeValueAsString(new ScmPriceFixture(new BigDecimal("12.34564"), "PRICED")))
                        .contains("\"unitPrice\":\"12.3456\"");
                assertThat(objectMapper.writeValueAsString(new ScmPriceFixture(new BigDecimal("8"), "PRICED")))
                        .contains("\"unitPrice\":\"8.0000\"");
            }

            // ------------------------------------------------------------------
            // 2. 对照：XSY 的序列化器在什么条件下才会把 null 变成 0
            // ------------------------------------------------------------------

            /**
             * 重要细节：Jackson 的 {@code @JsonSerialize(using = X)} <b>不会</b>作用于 null，
             * 只有 {@code nullsUsing = X} 才会。因此 {@code BigDecimalNullZeroSerializer} 的危险
             * 形式是 {@code nullsUsing}，而不是 {@code using}。
             */
            @Test
            @DisplayName("对照：using 不作用于 null，null 仍是 null")
            void usingDoesNotAffectNull() throws Exception {
                class UsingFixture {
                    @JsonSerialize(using = BigDecimalNullZeroSerializer.class)
                    public BigDecimal unitPrice;

                    UsingFixture(BigDecimal unitPrice) {
                        this.unitPrice = unitPrice;
                    }
                }

                assertThat(objectMapper.writeValueAsString(new UsingFixture(null)))
                        .isEqualTo("{\"unitPrice\":null}");
            }

            @Test
            @DisplayName("对照：nullsUsing 才会把 null 写成 0 —— 这正是 SCM 禁止的用法")
            void nullsUsingLosesNull() throws Exception {
                class NullsUsingFixture {
                    @JsonSerialize(nullsUsing = BigDecimalNullZeroSerializer.class)
                    public BigDecimal unitPrice;

                    NullsUsingFixture(BigDecimal unitPrice) {
                        this.unitPrice = unitPrice;
                    }
                }

                String json = objectMapper.writeValueAsString(new NullsUsingFixture(null));

                // 语义丢失：UNPRICED 被伪装成 0 元
                assertThat(json).contains("\"unitPrice\":0");
                assertThat(json).doesNotContain("null");
            }

            // ------------------------------------------------------------------
            // 3. 防回归：SCM 包内不得出现 BigDecimalNullZeroSerializer
            // ------------------------------------------------------------------

            @Test
            @DisplayName("门禁：SCM 包内不得有任何类或字段使用 BigDecimalNullZeroSerializer")
            void scmPackageMustNotUseNullZeroSerializer() throws Exception {
                List<String> offenders = new ArrayList<>();

                ClassPathScanningCandidateComponentProvider provider =
                        new ClassPathScanningCandidateComponentProvider(false);
                // 扫描全部候选组件（默认过滤器会排除接口与抽象类）
                provider.addIncludeFilter((metadataReader, metadataReaderFactory) -> true);

                Set<BeanDefinition> candidates = provider.findCandidateComponents(SCM_ROOT_PACKAGE);
                assertThat(candidates)
                        .as("SCM 根包 %s 下应能扫描到类；若为空说明包名或扫描配置有误", SCM_ROOT_PACKAGE)
                        .isNotEmpty();

                for (BeanDefinition definition : candidates) {
                    Class<?> clazz = Class.forName(definition.getBeanClassName());

                    if (hasNullZeroSerializer(clazz.getAnnotation(JsonSerialize.class))) {
                        offenders.add(clazz.getName() + " (类级注解)");
                    }
                    for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                        if (hasNullZeroSerializer(field.getAnnotation(JsonSerialize.class))) {
                            offenders.add(clazz.getName() + "#" + field.getName());
                        }
                    }
                }

                assertThat(offenders)
                        .as("BigDecimalNullZeroSerializer 会把 null 变成 0，破坏 SCM 的 UNPRICED 语义。"
                                + "请改用 ScmFixedScale4Serializer。违规位置：%s", offenders)
                        .isEmpty();
            }

            private static boolean hasNullZeroSerializer(JsonSerialize annotation) {
                if (annotation == null) {
                    return false;
                }
                if (annotation.using() == BigDecimalNullZeroSerializer.class) {
                    return true;
                }
                return annotation.nullsUsing() == BigDecimalNullZeroSerializer.class;
            }
        }

        @Nested

        class ScmOperatorNameContractTest {
            /** 字段名即操作人：{@code operator} / {@code auditor} 是固定名，其余一律以 {@code By} 结尾。 */
            private static final Pattern OPERATOR_FIELD = Pattern.compile(
                    "^\\s*private String (operator|auditor|[a-z][a-zA-Z]*By);$");

            /** 审计保存的数据：操作人藏在 Map 的键上，走的是另一个序列化器。 */
            private static final Pattern SNAPSHOT_FIELD = Pattern.compile(
                    "^\\s*private Map<String, Object> (beforeData|afterData);$");

            private static final String OP_ANNOTATION = "@JsonSerialize(using = ScmOperatorNameSerializer.class)";

            private static final String SNAPSHOT_ANNOTATION = "@JsonSerialize(using = ScmOperatorSnapshotSerializer.class)";

            @Test
            @DisplayName("每个操作人字段都带人名序列化注解，否则页面上会露出 userType:userId")
            void everyOperatorFieldCarriesTheNameSerializer() throws IOException {
                List<String> offenders = new ArrayList<>();
                int checked = 0;

                for (Path path : voFiles()) {
                    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (OPERATOR_FIELD.matcher(line).matches()) {
                            checked++;
                            if (!hasAnnotation(lines, i, OP_ANNOTATION)) {
                                offenders.add(relative(path) + " -> " + line.trim());
                            }
                        }
                        if (SNAPSHOT_FIELD.matcher(line).matches()) {
                            checked++;
                            if (!hasAnnotation(lines, i, SNAPSHOT_ANNOTATION)) {
                                offenders.add(relative(path) + " -> " + line.trim());
                            }
                        }
                    }
                }

                assertThat(checked).as("没扫到任何操作人字段，说明扫描规则或目录已经失效").isGreaterThan(20);
                assertThat(offenders)
                        .as("这些字段会直接输出 userType:userId，请加 @JsonSerialize(using = ScmOperatorNameSerializer.class)")
                        .isEmpty();
            }

            /** 注解必须紧贴在字段上一行（中间不能隔空行，否则读代码的人看不出它管的是哪个字段）。 */
            private static boolean hasAnnotation(List<String> lines, int fieldIndex, String annotation) {
                return fieldIndex > 0 && lines.get(fieldIndex - 1).trim().equals(annotation);
            }

            private static List<Path> voFiles() throws IOException {
                Path root = moduleRoot().resolve("src/main/java/com/xsy/scm");
                assertThat(root).as("SCM 源码目录必须存在，否则本断言形同虚设").exists();
                try (Stream<Path> paths = Files.walk(root)) {
                    return paths.filter(p -> p.toString().endsWith(".java"))
                            .filter(p -> p.getParent() != null && "vo".equals(p.getParent().getFileName().toString()))
                            .toList();
                }
            }

            private static String relative(Path path) {
                return moduleRoot().relativize(path).toString().replace('\\', '/');
            }

            private static Path moduleRoot() {
                Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
                for (int depth = 0; depth < 4 && current != null; depth++) {
                    if (Files.isDirectory(current.resolve("src/main/java/com/xsy/scm"))) {
                        return current;
                    }
                    current = current.getParent();
                }
                throw new IllegalStateException("找不到 xsy-admin 模块目录（user.dir = " + System.getProperty("user.dir") + "）");
            }
        }

        @Nested

        class ScmOperatorNameResolverTest {
            /** 员工表里只有 7 号员工；其余编号视为账号已被物理删除。 */
            private ScmOperatorNameResolver resolver() {
                EmployeeEntity employee = new EmployeeEntity();
                employee.setEmployeeId(7L);
                employee.setActualName("张三");
                EmployeeDao employeeDao = mock(EmployeeDao.class);
                when(employeeDao.selectList(any())).thenReturn(List.of(employee));
                return new ScmOperatorNameResolver(employeeDao);
            }

            @Test
            @DisplayName("员工编号换成姓名")
            void employeeIdBecomesName() {
                assertThat(resolver().display("1:7")).isEqualTo("张三");
            }

            @Test
            @DisplayName("员工表里查不到时给「已删除员工」，不露原始编号")
            void missingEmployeeDoesNotLeakRawId() {
                assertThat(resolver().display("1:999")).isEqualTo(ScmOperatorNameResolver.UNKNOWN_EMPLOYEE);
            }

            @Test
            @DisplayName("商城客户只给身份，不露账号编号")
            void mallCustomerShowsIdentityOnly() {
                assertThat(resolver().display("2:44")).isEqualTo(ScmOperatorNameResolver.MALL_CUSTOMER);
            }

            @Test
            @DisplayName("已经是人名或系统标识的值原样返回")
            void plainValuesStayUntouched() {
                assertThat(resolver().display("系统管理员")).isEqualTo("系统管理员");
                assertThat(resolver().display("SYSTEM")).isEqualTo("SYSTEM");
                assertThat(resolver().display(null)).isNull();
            }
        }

        @Nested

        class ScmOperatorNameSerializerTest {
            private static final String EMPLOYEE_ONE = "1:1";

            /** 装着保存当时资料的字段的壳，只为让注解生效，验证真实序列化路径。 */
            private record SnapshotHolder(
                    @JsonSerialize(using = ScmOperatorSnapshotSerializer.class) Map<String, Object> data) {
            }

            /** 装上假解析器：只认识 1:1；跑完恢复，避免影响同 JVM 的其它用例。 */
            private void withFakeResolver(Runnable body) {
                ScmOperatorNameSerializer.bind(raw -> EMPLOYEE_ONE.equals(raw) ? "系统管理员" : raw);
                try {
                    body.run();
                } finally {
                    ScmOperatorNameSerializer.bind(UnaryOperator.identity());
                }
            }

            @Test
            @DisplayName("只认管理端员工形状；真实人名、非员工 userType、坏数据一律返回 null")
            void onlyAdminEmployeeShapeIsParsed() {
                assertThat(ScmOperatorNameResolver.adminEmployeeId(EMPLOYEE_ONE)).isEqualTo(1L);
                assertThat(ScmOperatorNameResolver.adminEmployeeId("1:17")).isEqualTo(17L);

                assertThat(ScmOperatorNameResolver.adminEmployeeId("系统管理员")).isNull();
                // 其它 userType 的 id 与员工 id 不在同一空间，拿员工表查会张冠李戴
                assertThat(ScmOperatorNameResolver.adminEmployeeId("2:1")).isNull();
                assertThat(ScmOperatorNameResolver.adminEmployeeId("1:abc")).isNull();
                assertThat(ScmOperatorNameResolver.adminEmployeeId("1")).isNull();
                assertThat(ScmOperatorNameResolver.adminEmployeeId("")).isNull();
                assertThat(ScmOperatorNameResolver.adminEmployeeId(null)).isNull();
            }

            @Test
            @DisplayName("字段序列化：认得的换人名，认不得的原样输出")
            void serializesKnownOperatorAndKeepsOthers() {
                withFakeResolver(() -> {
                    assertThat(ScmOperatorNameSerializer.resolve(EMPLOYEE_ONE)).isEqualTo("系统管理员");
                    // 员工已删 / 查不到 → 回落原值，不吞数据
                    assertThat(ScmOperatorNameSerializer.resolve("1:999")).isEqualTo("1:999");
                    assertThat(ScmOperatorNameSerializer.resolve("李文博")).isEqualTo("李文博");
                    assertThat(ScmOperatorNameSerializer.resolve(null)).isNull();
                });
            }

            @Test
            @DisplayName("审计保存的数据：顶层与嵌套的操作人键都换人名，其它键不动")
            void snapshotRewritesOperatorKeysAtAnyDepth() throws Exception {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("status", "CANCELLED");
                snapshot.put("createdBy", EMPLOYEE_ONE);
                snapshot.put("address", new LinkedHashMap<>(Map.of("createdBy", EMPLOYEE_ONE, "receiverName", "收货部")));
                // 列表里的元素不在递归范围内：这是有意划的边界，避免把任意结构都走一遍
                snapshot.put("items", List.of(Map.of("operator", EMPLOYEE_ONE)));

                withFakeResolver(() -> {
                    String json;
                    try {
                        json = new ObjectMapper().writeValueAsString(new SnapshotHolder(snapshot));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    assertThat(json).contains("\"createdBy\":\"系统管理员\"");
                    assertThat(json).contains("\"status\":\"CANCELLED\"");
                    assertThat(json).contains("\"receiverName\":\"收货部\"");
                    assertThat(json).contains("\"operator\":\"1:1\"");
                });
            }
        }
    }

    @Nested
    @DisplayName("地图 M0 地理数据地基（V40 PG IT）")
    class ScmGeoMigrationIT extends ScmBaselineMigrationPgITBase {
        private static final String V40 = "V40__scm_geo_region_and_master_location.sql";

        private static final List<String> MASTER_TABLES = List.of("xsy_warehouse", "xsy_customer", "xsy_supplier");

        /**
         * V42 只给这三张表加了 {@code ck_…_location_complete}：配送链路用到的是仓库、客户与订单下单时保存的地址。
         * supplier 没有这条聚合约束，V40 的细粒度五条是它唯一的地理守卫 —— 所以负向用例的可接受拒绝原因
         * 逐表不同，不能统一按「细粒度或聚合」两个名字判。
         */
        private static final List<String> LOCATION_COMPLETE_TABLES =
                List.of("xsy_warehouse", "xsy_customer", "xsy_order_address_snapshot");

        // ------------------------------------------------------------------
        // 字典种子
        // ------------------------------------------------------------------

        @Test
        @DisplayName("区划字典：省市两级齐备、每个市都有质心、父子关系闭合")
        void regionSeedIsTwoLevelCompleteAndCentroidBearing() {
            assertThat(count("SELECT count(*) FROM xsy_region "
                    + "WHERE deleted = FALSE AND region_level = 1")).isEqualTo(34);
            assertThat(count("SELECT count(*) FROM xsy_region "
                    + "WHERE deleted = FALSE AND region_level = 2")).isEqualTo(414);

            // 气泡坐标完全来自质心：任何一行为空都会让那个市从地图上消失，且不产生任何错误
            assertThat(count("SELECT count(*) FROM xsy_region "
                    + "WHERE deleted = FALSE AND (center_lng IS NULL OR center_lat IS NULL)")).isZero();
            // 取值域按中国陆地范围放宽到含南海诸岛：越界即坐标写错，不是数据缺省
            assertThat(count("SELECT count(*) FROM xsy_region WHERE deleted = FALSE "
                    + "AND (center_lng NOT BETWEEN 73 AND 136 OR center_lat NOT BETWEEN 3 AND 54)")).isZero();

            assertThat(count("SELECT count(*) FROM xsy_region c WHERE c.deleted = FALSE AND c.region_level = 2 "
                    + "AND NOT EXISTS (SELECT 1 FROM xsy_region p "
                    + "WHERE p.deleted = FALSE AND p.region_level = 1 AND p.region_code = c.parent_code)")).isZero();
            // 没有下辖市的省会永远不着色；省名一旦被改名，map series 按名称就匹配不上了
            assertThat(count("SELECT count(*) FROM xsy_region p WHERE p.deleted = FALSE AND p.region_level = 1 "
                    + "AND NOT EXISTS (SELECT 1 FROM xsy_region c "
                    + "WHERE c.deleted = FALSE AND c.parent_code = p.region_code)")).isZero();

            assertThat(count("SELECT count(*) FROM (SELECT region_code FROM xsy_region "
                    + "WHERE deleted = FALSE GROUP BY region_code HAVING count(*) > 1) t")).isZero();
        }

        /**
         * 钉住「市名在全国不唯一」这一记录。
         *
         * <p>V40 解析规则里「市名不唯一时必须同时命中省名」那条分支不是防御性冗余 ——
         * 种子数据里确实存在重名市。断言它只有重庆市，是为了让重名清单变化时立刻暴露：
         * 届时消歧规则需要重新评估，而不是悄悄失守或变成死代码。
         */
        @Test
        @DisplayName("区划字典：市级重名只有重庆市，解析必须靠省名消歧")
        void duplicatedCityNamesAreExactlyTheDocumentedOne() {
            List<String> duplicated = jdbc.queryForList(
                    "SELECT region_name || '=' || count(*) FROM xsy_region "
                            + "WHERE deleted = FALSE AND region_level = 2 "
                            + "GROUP BY region_name HAVING count(*) > 1", String.class);

            assertThat(duplicated).containsExactly("重庆市=2");
            assertThat(jdbc.queryForList(
                    "SELECT region_code FROM xsy_region WHERE deleted = FALSE AND region_level = 2 "
                            + "AND region_name = '重庆市' ORDER BY region_code", Integer.class))
                    .containsExactly(500100, 500200);
        }

        // ------------------------------------------------------------------
        // 主档地理列约束
        // ------------------------------------------------------------------

        /**
         * 三张主档共用同一组 CHECK（由 {@code format()} 批量建出），逐表验证一次，
         * 防止「只给一张表加了约束」这种只在另两张表写入时才暴露的缺陷。
         *
         * <p>每个期望失败的语句都包在 SAVEPOINT 里：PostgreSQL 在报错后会把整个事务置为
         * aborted，不回滚到保存点就无法在同一用例里继续验证下一条约束和「合法值必须写得进」。
         */
        @Test
        @DisplayName("主档地理列：半套坐标、越界值与未知 CRS 一律被 DB 拒绝")
        void masterGeoColumnsRejectHalfCoordinatesAndUnknownCrs() {
            for (String table : MASTER_TABLES) {
                Long id = newMasterRow(table);

                // 只有经度没有纬度：既没法画也没法解释，M2 选点拿到这种行无从判断是否被截断过
                assertRejectedBy("半套坐标", rejectReasons(table, "coordinate_pair"),
                        "UPDATE " + table + " SET longitude = 120.155078 WHERE id = ?", id);

                // CRS 只能伴随坐标存在：标了 GCJ02 却没坐标，等于声明了一个无法验证的规则
                assertRejectedBy("带坐标系却没坐标", rejectReasons(table, "crs_with_coordinate"),
                        "UPDATE " + table + " SET geom_crs = 'GCJ02' WHERE id = ?", id);

                // 白名单之外的大小写变体必须进不来，否则两套坐标系会伪装成一套
                assertRejectedBy("CRS 大小写变体", rejectReasons(table, "geom_crs"),
                        "UPDATE " + table + " SET longitude = 120.155078, latitude = 30.259244, "
                                + "geom_crs = 'gcj-02' WHERE id = ?", id);

                // 其余字段全合法，只让经度越界：拒绝原因才能归到取值域本身，而不是缺了 CRS
                assertRejectedBy("经度越界", rejectReasons(table, "longitude"),
                        "UPDATE " + table + " SET longitude = 181, latitude = 30.259244, "
                                + "geom_crs = 'GCJ02' WHERE id = ?", id);

                assertRejectedBy("纬度越界", rejectReasons(table, "latitude"),
                        "UPDATE " + table + " SET longitude = 120.155078, latitude = 91, "
                                + "geom_crs = 'GCJ02' WHERE id = ?", id);

                // 成对 + 白名单 + 域内：这条必须写得进，否则上面的拒绝只是「列根本没法用」
                assertThat(jdbc.update("UPDATE " + table + " SET longitude = 120.155078, latitude = 30.259244, "
                        + "geom_crs = 'GCJ02' WHERE id = ?", id)).isEqualTo(1);
            }
        }

        /**
         * 逐表钉住 V40 的细粒度地理 CHECK 与 V42 的聚合 CHECK 都还在。
         *
         * <p>负向用例只能断言「拒绝出自这几条约束之一」，所以某条约束被删掉不会让那里变红，
         * 必须由这里保证：细粒度约束一旦消失，规则就只剩聚合约束一份，而它并不按列拆分原因。
         */
        @Test
        @DisplayName("主档地理约束：V40 细粒度五条逐表齐备，V42 聚合约束按配送链路分布")
        void geoCheckConstraintsExistOnEveryMaster() {
            for (String table : MASTER_TABLES) {
                List<String> present = jdbc.queryForList(
                        "SELECT con.conname FROM pg_constraint con "
                                + "JOIN pg_class c ON c.oid = con.conrelid "
                                + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                                + "WHERE con.contype = 'c' AND n.nspname = current_schema() AND c.relname = ?",
                        String.class, table);

                assertThat(present).as("%s 的 V40 细粒度地理 CHECK 缺失", table).contains(
                        "ck_" + constraintBase(table) + "_geom_crs",
                        "ck_" + constraintBase(table) + "_longitude",
                        "ck_" + constraintBase(table) + "_latitude",
                        "ck_" + constraintBase(table) + "_coordinate_pair",
                        "ck_" + constraintBase(table) + "_crs_with_coordinate");

                // 聚合约束决定负向用例允许哪些拒绝原因，所以它的有无也要钉住，不能只靠常量集合自证
                String umbrella = "ck_" + constraintBase(table) + "_location_complete";
                if (LOCATION_COMPLETE_TABLES.contains(table)) {
                    assertThat(present).as("%s 缺少 V42 聚合约束 %s", table, umbrella).contains(umbrella);
                } else {
                    assertThat(present).as("%s 多出 V42 聚合约束 %s", table, umbrella).doesNotContain(umbrella);
                }
            }
        }

        @Test
        @DisplayName("主档按市聚合走 city_code 部分索引，且只覆盖活动且已归属的行")
        void cityCodePartialIndexExistsOnEveryMaster() {
            for (String table : MASTER_TABLES) {
                List<String> definitions = jdbc.queryForList(
                        "SELECT indexdef FROM pg_indexes WHERE schemaname = current_schema() "
                                + "AND tablename = ? AND indexdef LIKE '%city_code%'", String.class, table);

                assertThat(definitions)
                        .as("%s 缺少按市聚合的部分索引：大屏 geo 聚合会退化成全表扫", table)
                        .anyMatch(def -> def.contains("deleted") && def.contains("city_code IS NOT NULL"));
            }
        }

        // ------------------------------------------------------------------
        // 存量地址保守解析
        // ------------------------------------------------------------------

        /**
         * 重放 V40 Step 4，逐条验证「宁缺勿错」的规则。
         *
         * <p>用例包在事务里、结束回滚，所以可以在真实字典上重放整段 UPDATE 而不污染库。
         */
        @Test
        @DisplayName("存量地址解析：只认完整市名、重名靠省名消歧、解析不出留 NULL、防重复处理不覆盖")
        void conservativeAddressParseMatchesOnlyFullCityNames() {
            Long fullAddress = newCustomer();
            Long streetTrap = newCustomer();
            Long ambiguousCity = newCustomer();
            Long provinceOnly = newCustomer();
            Long unparseable = newCustomer();

            address(fullAddress, "浙江省杭州市西湖区文三路100号");
            // 「长沙路」是街道，不是长沙市的证据；同一串里的「上海市」才是
            address(streetTrap, "上海市浦东新区长沙路88号");
            address(ambiguousCity, "重庆市渝北区龙溪街道1号");
            address(provinceOnly, "吉林省某区某街1号");
            address(unparseable, "某某工业园 A 栋 3 层");

            runConservativeParseSection();

            assertThat(geoColumnsOf(fullAddress)).containsExactly(330000, 330100, "浙江省", "杭州市");
            assertThat(geoColumnsOf(streetTrap)).containsExactly(310000, 310100, "上海市", "上海市");
            // 两条同名市里取编码较小者：并列取小才能让同一地址重放两次得到同一个结果
            assertThat(geoColumnsOf(ambiguousCity)).containsExactly(500000, 500100, "重庆市", "重庆市");
            // 只解析到市：市解析不出时至少把省补齐，让省级统计不丢行
            assertThat(geoColumnsOf(provinceOnly)).containsExactly(220000, null, "吉林省", null);
            assertThat(geoColumnsOf(unparseable)).containsExactly(null, null, null, null);

            // 防重复处理：解析段只碰 city_code IS NULL 的行，已人工归属的绝不覆写
            assertThat(jdbc.update("UPDATE xsy_customer SET province_code = 440000, city_code = 440100 WHERE id = ?",
                    unparseable)).isEqualTo(1);
            runConservativeParseSection();
            assertThat(geoColumnsOf(unparseable)).containsExactly(440000, 440100, null, null);
        }

        @Test
        @DisplayName("解析只到市：区县由人工选择维护，解析段不碰区县两列")
        void districtCodeIsNeverTouchedByTheParse() {
            Long customerId = newCustomer();
            address(customerId, "浙江省杭州市余杭区文一西路969号");
            assertThat(jdbc.update("UPDATE xsy_customer SET district_code = 330110, district_name = '余杭区' "
                    + "WHERE id = ?", customerId)).isEqualTo(1);

            runConservativeParseSection();

            assertThat(geoColumnsOf(customerId)).containsExactly(330000, 330100, "浙江省", "杭州市");
            assertThat(jdbc.queryForObject(
                    "SELECT district_code || '/' || district_name FROM xsy_customer WHERE id = ?",
                    String.class, customerId)).isEqualTo("330110/余杭区");
        }

        // ------------------------------------------------------------------
        // 辅助
        // ------------------------------------------------------------------

        /**
         * 执行 V40 的解析段原文。
         *
         * <p>取 {@code -- Step 4} 到文件末尾（它是文件的最后一段）；标记被改名时索引返回 -1，
         * 断言立刻失败，不会静默跳过被测 SQL。
         */
        private void runConservativeParseSection() {
            String sql = migrationSql(V40);
            int from = sql.indexOf("-- Step 4");
            assertThat(from).as("%s 中找不到 -- Step 4 段（标记被改名了？）", V40).isGreaterThanOrEqualTo(0);
            jdbc.execute(toCurrentTableNames(sql.substring(from)));
        }

        /**
         * 在保存点里执行一条期望被拒绝的语句，随后回滚保存点以保持事务可用。
         *
         * <p>仍然断言拒绝出自哪条约束：只判「抛异常」的话，列名写错、语法错误同样会拒绝，
         * 用例会在约束被删掉后依然绿着。但允许的是名字集合而不是单个名字 —— V42 的
         * {@code ck_…_location_complete} 把成对、取值域与 CRS 白名单整体重写了一遍，
         * 同一行往往同时违反细粒度约束和该聚合约束，而 PostgreSQL 只报它先求值到的那条。
         * 细粒度约束本身是否还在，由 {@link #geoCheckConstraintsExistOnEveryMaster()} 钉住。
         */
        private void assertRejectedBy(String rule, List<String> allowed, String sql, Object... args) {
            jdbc.execute("SAVEPOINT geo_case");
            String message = null;
            try {
                jdbc.update(sql, args);
            } catch (DataAccessException e) {
                message = rootMessage(e);
            }
            jdbc.execute("ROLLBACK TO SAVEPOINT geo_case");
            String rejection = message;
            assertThat(rejection).as("期望按「%s」被拒绝，实际未被拒绝", rule).isNotNull();
            assertThat(allowed).as("拒绝原因不属于「%s」的任何一条约束，实际: %s", rule, rejection)
                    .anyMatch(rejection::contains);
        }

        /** 某条细粒度规则的可接受拒绝原因：它自己，加上（该表有的话）覆盖同一条规则的 V42 聚合约束。 */
        /**
         * 约束名基于 V118 之前的表名：{@code ALTER TABLE ... RENAME} 不改约束名，
         * 因此拼 {@code ck_…} 时要去掉 {@code xsy_} 前缀。
         */
        private static String constraintBase(String table) {
            return table.startsWith("xsy_") ? table.substring(4) : table;
        }

        private static List<String> rejectReasons(String table, String constraint) {
            List<String> allowed = new ArrayList<>(List.of("ck_" + constraintBase(table) + "_" + constraint));
            if (LOCATION_COMPLETE_TABLES.contains(table)) {
                allowed.add("ck_" + constraintBase(table) + "_location_complete");
            }
            return allowed;
        }

        private static String rootMessage(Throwable throwable) {
            Throwable current = throwable;
            while (current.getCause() != null) {
                current = current.getCause();
            }
            return current.getMessage();
        }

        /**
         * 三张主档各取一条新建行（走服务写入口，不直接插表）。
         */
        private Long newMasterRow(String table) {
            return switch (table) {
                case "xsy_warehouse" -> newWarehouse("GEO-W");
                case "xsy_supplier" -> newSupplier("GEO-S");
                default -> newCustomer();
            };
        }

        private void address(Long customerId, String value) {
            assertThat(jdbc.update("UPDATE xsy_customer SET address = ?, province_code = NULL, province_name = NULL, "
                    + "city_code = NULL, city_name = NULL WHERE id = ?", value, customerId)).isEqualTo(1);
            evictMybatisCache();
        }

        /**
         * 归属四元组：省码 / 市码 / 省名 / 市名；null 原样保留，用来断言「没解析出来」。
         */
        private List<Object> geoColumnsOf(Long customerId) {
            evictMybatisCache();
            Map<String, Object> row = jdbc.queryForList(
                    "SELECT province_code, city_code, province_name, city_name FROM xsy_customer WHERE id = ?",
                    customerId).stream().findFirst().orElseThrow();
            return Arrays.asList(row.get("province_code"), row.get("city_code"),
                    row.get("province_name"), row.get("city_name"));
        }

        private int count(String sql) {
            Integer value = jdbc.queryForObject(sql, Integer.class);
            return value == null ? 0 : value;
        }
    }

    @Nested
    @SpringBootTest(classes = XsyApplication.class, properties = {
            "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
            "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
            "file.storage.local.url-prefix=http://127.0.0.1:18082",
            "logging.level.root=WARN"})
    class ScmMigrationTargetPgIT extends ScmBaselineMigrationPgITBase {
        @Autowired
        private JdbcTemplate jdbc;

        /**
         * 保存模块基线的目标版本；当前V1001结构另由ScmBackendRulesPgIT验证。
         *
         * <p>这些断言验证已保存模块的历史结构，不作为当前业务库仍存在触发器和CHECK的证明。
         */
        private static String targetVersion() {
            String override = System.getProperty("xsy.migration.expected-version");
            if (override != null && !override.isBlank()) {
                return override.trim();
            }
            return "1000.24";
        }

        @Test
        void reachesTheRequestedMigrationVersion() {
            String expected = targetVersion();
            String current = jdbc.queryForObject("SELECT version FROM flyway_schema_history "
                    + "WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1", String.class);
            assertThat(current).isEqualTo(expected);
        }

        /**
         * 数据库版本达到 V128 时，必须包含券模板领取限制、订单预选券和券发放来源字段。
         * 目标版本早于 V128 时跳过检查。
         */
        @Test
        void mallClaimColumnsExistWhenTargetIs128OrLater() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 128) {
                return;
            }
            assertColumn("xsy_promotion_coupon", "claimable");
            assertColumn("xsy_promotion_coupon", "per_customer_limit");
            assertColumn("xsy_promotion_coupon", "total_limit");
            assertColumn("xsy_sales_order", "mall_coupon_instance_id");
            assertColumn("xsy_promotion_coupon_instance", "issue_source");
        }

        /**
         * 数据库版本达到 V129 时，订单必须包含结算方式列和取值约束。
         * 结算方式仅表示约定，余额、授信和收款仍由各自业务模块管理。
         */
        @Test
        void settlementMethodColumnExistsWhenTargetIs129OrLater() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 129) {
                return;
            }
            assertColumn("xsy_sales_order", "settlement_method");
            Integer allowed = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_sales_order_settlement_method'",
                    Integer.class);
            assertThat(allowed).as("结算方式取值约束应随列一起建立").isEqualTo(1);
        }

        /**
         * 数据库禁止外键约束（见 AGENTS.md）。V127 清理最后一个外键后，
         * xsy schema 的外键数量必须保持为零。
         */
        @Test
        void noForeignKeysAcrossSchema() {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint c "
                            + "WHERE c.contype = 'f' AND c.connamespace = 'xsy'::regnamespace",
                    Integer.class);
            assertThat(count).isZero();
        }

        @Test
        void orderExceptionCompensationTablesExistFromV148() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 148) {
                return;
            }
            if (target >= 220) {
                // V220 把 1:1 的换货关联收进退货单本身
                assertColumn("xsy_order_return", "replacement_order_id");
            } else {
                assertColumn("xsy_order_exchange_link", "return_id");
                assertColumn("xsy_order_exchange_link", "replacement_order_id");
                Integer uniqueIndexes = jdbc.queryForObject(
                        "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy' "
                                + "AND indexname IN ('uk_order_exchange_return', 'uk_order_exchange_replacement')",
                        Integer.class);
                assertThat(uniqueIndexes).isEqualTo(2);
            }
            assertColumn("xsy_order_received_quantity_correction", "previous_received_quantity");
            assertColumn("xsy_order_received_quantity_correction", "shipped_quantity");
        }

        /**
         * V173～V175：包装码称重与公开令牌、溯源附件类型、公开查询审计表。
         *
         * <p>
         * 断言的是「列与约束真的建出来了」，而不是迁移文件存在：NOT NULL 与唯一索引缺失时，
         * 服务端写入才会暴露问题，而那时已经在跑业务了。
         */
        @Test
        void traceWeightTokenAndAttachmentSchemaExistsFromV173() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 175) {
                return;
            }
            assertColumn("xsy_trace_unit", "public_token");
            assertColumn("xsy_trace_unit", "net_weight");
            assertColumn("xsy_trace_unit", "weighing_event_id");
            assertColumn("xsy_trace_public_query", "public_token");
            Integer tokenIndex = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy' "
                            + "AND indexname IN ('uk_trace_unit_public_token', 'uk_trace_unit_weighing_event')",
                    Integer.class);
            assertThat(tokenIndex).isEqualTo(2);
            // 附件白名单：两个新类型必须真的落在 CHECK 里，否则关系行写不进去
            String bizTypeCheck = jdbc.queryForObject(
                    "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                            + "WHERE conname = 'ck_t_file_relation_biz_type'",
                    String.class);
            assertThat(bizTypeCheck).contains("PRODUCT_QUALIFICATION").contains("LOT_INSPECTION");
            // 称重事件类型：补录与设备称重都要能写入数据库
            String eventCheck = jdbc.queryForObject(
                    "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_trace_event_type'",
                    String.class);
            assertThat(eventCheck).contains("WEIGH");
        }

        @Test
        void codPermissionDefaultsToDisabledFromV130() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 130) {
                return;
            }
            assertColumn("xsy_customer", "cod_allowed");
            Integer notAllowed = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_customer WHERE cod_allowed IS NULL", Integer.class);
            assertThat(notAllowed).isZero();
            String defaultExpression = jdbc.queryForObject(
                    "SELECT column_default FROM information_schema.columns "
                            + "WHERE table_schema = 'xsy' AND table_name = 'xsy_customer' AND column_name = 'cod_allowed'",
                    String.class);
            assertThat(defaultExpression).contains("false");
        }

        @Test
        void codInstallmentOrderColumnExistsFromV132() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 132) {
                return;
            }
            assertColumn("xsy_finance_receipt", "cod_order_id");
        }

        @Test
        void mallWarehouseAndConversionSchemaExistsAt137() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 137) {
                return;
            }
            assertColumn("xsy_customer", "mall_delivery_warehouse_id");
            assertColumn("xsy_sales_order", "mall_delivery_warehouse_id");
            assertColumn("xsy_sales_order_item", "mall_stock_unit_snapshot");
            assertColumn("xsy_sales_order_item", "mall_stock_units_per_sale_unit");
            assertColumn("xsy_inventory_outbound_item", "stock_quantity");
            assertColumn("xsy_order_return_receipt_item", "stock_quantity");
            assertColumn("xsy_order_return_receipt_item", "stock_unit_snapshot");
            assertColumn("xsy_product_sku_stock_conversion", "stock_units_per_sale_unit");
            assertColumn("xsy_mall_district_warehouse", "priority");

            Integer constraints = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE connamespace = 'xsy'::regnamespace "
                            + "AND conname IN ('ck_order_item_mall_stock_factor', "
                            + "'ck_order_return_receipt_stock_quantity', 'ck_order_return_receipt_stock_snapshot_pair')",
                    Integer.class);
            assertThat(constraints).isEqualTo(3);
            Integer indexes = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname='xsy' "
                            + "AND indexname IN ('uk_mall_district_warehouse_active', "
                            + "'uk_product_sku_stock_conversion_active')", Integer.class);
            assertThat(indexes).isEqualTo(2);
        }

        @Test
        void sortingPartRecordSchemaExistsAt141() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 141)
                return;
            assertColumn("xsy_sorting_part_record", "task_id");
            assertColumn("xsy_sorting_part_record", "task_item_id");
            assertColumn("xsy_sorting_part_record", "quantity_delta");
            assertColumn("xsy_sorting_part_record", "total_quantity");
            Integer checks = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE connamespace='xsy'::regnamespace "
                            + "AND conname IN ('ck_sorting_part_record_event','ck_sorting_part_record_delta',"
                            + "'ck_sorting_part_record_total')", Integer.class);
            assertThat(checks).isEqualTo(3);
        }

        @Test
        void purchaseInquiryAndImmutableQuoteSchemaExistAt143() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 143)
                return;
            assertColumn("xsy_purchase_inquiry", "accepted_quote_id");
            assertColumn("xsy_purchase_inquiry", "required_quantity");
            assertColumn("xsy_purchase_inquiry_quote", "revision");
            assertColumn("xsy_purchase_inquiry_quote", "valid_until");
            Integer guards = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_trigger WHERE tgname IN "
                            + "('trg_purchase_inquiry_guard', 'trg_purchase_inquiry_quote_immutable') "
                            + "AND NOT tgisinternal", Integer.class);
            assertThat(guards).isEqualTo(2);
        }

        @Test
        void personalFilterPresetSchemaExistsAt144() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 144) return;
            assertColumn("xsy_user_filter_preset", "owner_key");
            assertColumn("xsy_user_filter_preset", "view_key");
            assertColumn("xsy_user_filter_preset", "conditions");
            Integer unique = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE connamespace = 'xsy'::regnamespace "
                            + "AND conname = 'uk_user_filter_preset_name'", Integer.class);
            assertThat(unique).isEqualTo(1);
        }

        @Test
        void orderTagSchemaExistsAt145() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 145) return;
            assertColumn("xsy_order_tag", "name");
            assertColumn("xsy_order_tag", "color");
            assertColumn("xsy_order_tag_link", "order_id");
            assertColumn("xsy_order_tag_link", "tag_id");
            Integer indexes = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname='xsy' "
                            + "AND indexname IN ('uk_order_tag_name','idx_order_tag_link_tag_order')",
                    Integer.class);
            assertThat(indexes).isEqualTo(2);
        }

        @Test
        void salesOrderPrintTypeAndDefaultTemplateExistAt146() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 146) return;
            Integer defaults = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_print_template "
                            + "WHERE document_type='SALES_ORDER' AND template_code='SALES_ORDER_DEFAULT' "
                            + "AND default_flag=TRUE AND enabled_flag=TRUE AND deleted=FALSE",
                    Integer.class);
            assertThat(defaults).isEqualTo(1);
            Integer constraints = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE connamespace='xsy'::regnamespace "
                            + "AND conname IN ('ck_scm_print_template_document_type', "
                            + "'ck_scm_print_record_document_type') "
                            + "AND pg_get_constraintdef(oid) LIKE '%SALES_ORDER%'",
                    Integer.class);
            assertThat(constraints).isEqualTo(2);
        }

        /**
         * V151 起采购收货可登记批次：仓库开关、批次档案、收货行批次记录、批次子账（余额 + 只能新增流水）。
         * 批次是独立子账，总账唯一键不变。
         */
        @Test
        void inventoryLotSchemaExistsFromV151() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 151) {
                return;
            }
            assertColumn("xsy_warehouse", "lot_tracking_enabled");
            assertColumn("xsy_trace_lot", "lot_no");
            assertColumn("xsy_trace_lot", "source_lot_no");
            assertColumn("xsy_purchase_receipt_item_lot", "lot_id");
            assertColumn("xsy_inventory_lot_balance", "quantity");
            assertColumn("xsy_inventory_lot_movement", "before_quantity");
            assertColumn("xsy_inventory_lot_movement", "after_quantity");
            Integer uniqueIndexes = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy' "
                            + "AND indexname IN ('uk_trace_lot_no', 'uk_inventory_lot_balance', "
                            + "'uk_purchase_receipt_item_lot')",
                    Integer.class);
            assertThat(uniqueIndexes).as("批次唯一索引应随表一起建立").isEqualTo(3);
            Integer appendOnly = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_inventory_lot_movement_append_only' "
                            + "AND NOT tgisinternal",
                    Integer.class);
            assertThat(appendOnly).as("批次流水必须只能新增").isEqualTo(1);
            Integer menus = jdbc.queryForObject(
                    "SELECT count(*) FROM t_menu WHERE menu_id = 2100 AND menu_type = 2", Integer.class);
            assertThat(menus).as("批次库存页面应随迁移发布").isEqualTo(1);
            Integer perms = jdbc.queryForObject(
                    "SELECT count(*) FROM t_menu WHERE menu_id = 2101 "
                            + "AND api_perms = 'scm:inventory:lot:query'", Integer.class);
            assertThat(perms).as("批次查询权限应随迁移发布").isEqualTo(1);
        }

        /**
         * V152 起包装码（一物一码）：单元表 + 只能新增事件表，码不透明且唯一。
         */
        @Test
        void traceUnitSchemaExistsFromV152() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 152) {
                return;
            }
            assertColumn("xsy_trace_unit", "unit_code");
            assertColumn("xsy_trace_unit", "lot_id");
            assertColumn("xsy_trace_unit", "package_type");
            assertColumn("xsy_trace_event", "event_type");
            Integer uniqueIndexes = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy' AND indexname = 'uk_trace_unit_code'",
                    Integer.class);
            assertThat(uniqueIndexes).as("包装码必须全局唯一").isEqualTo(1);
            Integer appendOnly = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_trace_event_append_only' AND NOT tgisinternal",
                    Integer.class);
            assertThat(appendOnly).as("包装事件必须只能新增").isEqualTo(1);
            Integer menus = jdbc.queryForObject(
                    "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 2110 AND 2112", Integer.class);
            assertThat(menus).as("包装码页面与两条权限应随迁移发布").isEqualTo(3);
        }

        /**
         * V153 起包装码可绑到分拣任务行：一码一物、绑定只能新增。
         */
        @Test
        void traceUnitBindingSchemaExistsFromV153() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 153) {
                return;
            }
            assertColumn("xsy_trace_unit_binding", "unit_id");
            assertColumn("xsy_trace_unit_binding", "sorting_task_item_id");
            assertColumn("xsy_trace_unit_binding", "customer_name_snapshot");
            // V157 起「一码一绑」改为「同一时刻只有一条有效绑定」：唯一约束被去掉，由触发器保证，
            // 这样解绑后可以重新绑定。历史断言按目标版本分流，避免把已演进的约束当成永恒记录。
            Integer uniqueIndexes = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy' "
                            + "AND indexname = 'uk_trace_unit_binding_unit'",
                    Integer.class);
            if (target >= 157) {
                assertThat(uniqueIndexes).as("V157 起唯一约束由有效绑定触发器取代").isZero();
                Integer activeTrigger = jdbc.queryForObject(
                        "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_trace_unit_binding_active' "
                                + "AND NOT tgisinternal", Integer.class);
                assertThat(activeTrigger).as("有效绑定唯一必须由触发器兜底").isEqualTo(1);
            } else {
                assertThat(uniqueIndexes).as("一个包装码只能绑一次").isEqualTo(1);
            }
            Integer appendOnly = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_trace_unit_binding_append_only' "
                            + "AND NOT tgisinternal",
                    Integer.class);
            assertThat(appendOnly).as("绑定记录必须只能新增").isEqualTo(1);
            Integer eventTypes = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_trace_event_type' "
                            + "AND pg_get_constraintdef(oid) LIKE '%BIND%'",
                    Integer.class);
            assertThat(eventTypes).as("绑定事件类型应已放开").isEqualTo(1);
            Integer menus = jdbc.queryForObject(
                    "SELECT count(*) FROM t_menu WHERE menu_id = 2113 "
                            + "AND api_perms = 'scm:inventory:unit:bind'", Integer.class);
            assertThat(menus).as("绑定权限应随迁移发布").isEqualTo(1);
        }

        /**
         * V154 起包装码标签走打印中心：单据类型放开到 TRACE_UNIT，并自带默认标签模板。
         */
        @Test
        void traceUnitPrintSchemaExistsFromV154() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 154) {
                return;
            }
            Integer templateConstraint = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_scm_print_template_document_type' "
                            + "AND pg_get_constraintdef(oid) LIKE '%TRACE_UNIT%'",
                    Integer.class);
            assertThat(templateConstraint).as("模板类型应放开到包装码").isEqualTo(1);
            Integer recordConstraint = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_scm_print_record_document_type' "
                            + "AND pg_get_constraintdef(oid) LIKE '%TRACE_UNIT%'",
                    Integer.class);
            assertThat(recordConstraint).as("打印记录类型应放开到包装码").isEqualTo(1);
            Integer templates = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_print_template WHERE document_type = 'TRACE_UNIT' "
                            + "AND default_flag = TRUE AND enabled_flag = TRUE AND deleted = FALSE",
                    Integer.class);
            assertThat(templates).as("包装码标签默认模板应存在且唯一").isEqualTo(1);
            String paper = jdbc.queryForObject(
                    "SELECT model->>'paper' FROM xsy_print_template WHERE document_type = 'TRACE_UNIT' "
                            + "AND default_flag = TRUE AND deleted = FALSE",
                    String.class);
            assertThat(paper).as("标签用 80mm 热敏纸").isEqualTo("TICKET_80");
        }

        /**
         * V177 起包装码标签模板包含扫码地址，前端据此生成二维码。
         */
        @Test
        void traceUnitLabelCarriesQrUrlFromV177() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 177) {
                return;
            }
            Integer missing = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_print_template WHERE document_type = 'TRACE_UNIT' "
                            + "AND enabled_flag = TRUE AND deleted = FALSE "
                            + "AND NOT (model->'headerFields') @> '[\"traceUrl\"]'::jsonb",
                    Integer.class);
            assertThat(missing).as("启用的包装码标签模板都应包含扫码地址").isZero();
        }

        /**
         * V155 起包装码可以作废：作废权限随迁移发布。
         */
        @Test
        void traceUnitVoidPermissionExistsFromV155() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 155) {
                return;
            }
            Integer menus = jdbc.queryForObject(
                    "SELECT count(*) FROM t_menu WHERE menu_id = 2114 "
                            + "AND api_perms = 'scm:inventory:unit:void'", Integer.class);
            assertThat(menus).as("作废权限应随迁移发布").isEqualTo(1);
        }

        /**
         * V156 起数据库兜底：绑定不能挂到作废码上。
         */
        @Test
        void traceUnitBindingActiveGuardExistsFromV156() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 156) {
                return;
            }
            Integer trigger = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_trace_unit_binding_active' "
                            + "AND NOT tgisinternal",
                    Integer.class);
            assertThat(trigger).as("绑定到作废码必须被数据库拒绝").isEqualTo(1);
        }

        /**
         * V157 起更正流程：申请单 + 解绑记录，有效绑定唯一由触发器保证。
         */
        @Test
        void traceUnitCorrectionSchemaExistsFromV157() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 157) {
                return;
            }
            assertColumn("xsy_trace_unit_correction", "binding_id");
            assertColumn("xsy_trace_unit_unbind", "correction_id");
            Integer uniqueIndexes = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy' "
                            + "AND indexname = 'uk_trace_unit_correction_pending'", Integer.class);
            assertThat(uniqueIndexes).as("一个码只能有一条待审批申请").isEqualTo(1);
            Integer dropped = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE conname = 'uk_trace_unit_binding_unit'",
                    Integer.class);
            assertThat(dropped).as("旧的「一码一绑」唯一约束应被移除，改由有效绑定触发器保证").isZero();
            Integer eventTypes = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_trace_event_type' "
                            + "AND pg_get_constraintdef(oid) LIKE '%UNBIND%'", Integer.class);
            assertThat(eventTypes).as("解绑事件类型应已放开").isEqualTo(1);
            Integer menus = jdbc.queryForObject(
                    "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 2115 AND 2117", Integer.class);
            assertThat(menus).as("更正页面与两条权限应随迁移发布").isEqualTo(3);
        }

        /** V158：有效绑定共享码行锁，正式出库与解绑共享原订单行锁。 */
        @Test
        void traceUnitConsistencyGuardsExistFromV158() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 158) {
                return;
            }
            Integer triggers = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_trigger t "
                            + "JOIN pg_class c ON c.oid=t.tgrelid "
                            + "JOIN pg_namespace n ON n.oid=c.relnamespace "
                            + "WHERE n.nspname='xsy' AND NOT t.tgisinternal AND t.tgname IN "
                            + "('trg_trace_unit_binding_active', 'trg_trace_unbind_outbound_guard', "
                            + "'trg_trace_lock_outbound_source_item', 'trg_trace_lock_outbound_confirmation', "
                            + "'trg_trace_unit_void_active_guard')",
                    Integer.class);
            assertThat(triggers).as("绑定、解绑、出库、作废五个数据库触发器必须存在").isEqualTo(5);
            String bindSource = jdbc.queryForObject(
                    "SELECT p.prosrc FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace "
                            + "WHERE n.nspname='xsy' AND p.proname = 'xsy_assert_unit_bindable'", String.class);
            assertThat(bindSource).contains("FOR UPDATE", "xsy_inventory_outbound_item");
            String unbindSource = jdbc.queryForObject(
                    "SELECT p.prosrc FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace "
                            + "WHERE n.nspname='xsy' AND p.proname = 'xsy_guard_trace_unit_unbind_outbound'", String.class);
            assertThat(unbindSource).contains("FOR UPDATE", "xsy_inventory_outbound_item");
        }

        /**
         * V159 起采购单可二维码分享：令牌只存哈希、一单一条有效链接、回填只能新增。
         */
        @Test
        void purchaseShareSchemaExistsFromV159() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 159) {
                return;
            }
            assertColumn("xsy_purchase_share_token", "token_hash");
            assertColumn("xsy_purchase_share_token", "expires_at");
            assertColumn("xsy_purchase_share_feedback", "purchase_order_item_id");
            Integer uniqueIndexes = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy' "
                            + "AND indexname IN ('uk_purchase_share_token_hash', 'uk_purchase_share_active_order')",
                    Integer.class);
            assertThat(uniqueIndexes).as("令牌哈希唯一且一单只有一条有效链接").isEqualTo(2);
            Integer appendOnly = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_purchase_share_feedback_append_only' "
                            + "AND NOT tgisinternal",
                    Integer.class);
            assertThat(appendOnly).as("回填记录必须只能新增").isEqualTo(1);
            Integer menus = jdbc.queryForObject(
                    "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 2118 AND 2119", Integer.class);
            assertThat(menus).as("分享与撤销权限应随迁移发布").isEqualTo(2);
        }

        /**
         * V160 起客户可发推广二维码：一客一条有效码，扫码记录只能新增。
         */
        @Test
        void customerPromotionSchemaExistsFromV160() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 160) {
                return;
            }
            assertColumn("xsy_customer_promotion_code", "code");
            assertColumn("xsy_customer_promotion_scan", "seller_id");
            Integer uniqueIndexes = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy' "
                            + "AND indexname IN ('uk_customer_promotion_code', 'uk_customer_promotion_active')",
                    Integer.class);
            assertThat(uniqueIndexes).as("码全局唯一且一客只有一条有效码").isEqualTo(2);
            Integer appendOnly = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_customer_promotion_scan_append_only' "
                            + "AND NOT tgisinternal",
                    Integer.class);
            assertThat(appendOnly).as("扫码记录必须只能新增").isEqualTo(1);
            Integer menus = jdbc.queryForObject(
                    "SELECT count(*) FROM t_menu WHERE menu_id = 2120 "
                            + "AND api_perms = 'scm:customer:promotion:manage'", Integer.class);
            assertThat(menus).as("推广码权限应随迁移发布").isEqualTo(1);
        }

        /**
         * V161 起有时价：规格级、区间半开、价格来源放开到 TIME_PRICE。
         */
        @Test
        void timePriceSchemaExistsFromV161() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 161) {
                return;
            }
            assertColumn("xsy_pricing_time_price", "unit_price");
            assertColumn("xsy_pricing_time_price", "start_at");
            Integer draftSource = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_sales_order_item_draft_source' "
                            + "AND pg_get_constraintdef(oid) LIKE '%TIME_PRICE%'", Integer.class);
            assertThat(draftSource).as("下单价格来源应放开到时价").isEqualTo(1);
            Integer lockedSource = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_sales_order_item_locked_source' "
                            + "AND pg_get_constraintdef(oid) LIKE '%TIME_PRICE%'", Integer.class);
            assertThat(lockedSource).as("锁价来源应放开到时价").isEqualTo(1);
            Integer menus = jdbc.queryForObject(
                    "SELECT count(*) FROM t_menu WHERE menu_id IN (2121, 2122)", Integer.class);
            assertThat(menus).as("时价页面与维护权限应随迁移发布").isEqualTo(2);
        }

        /**
         * V162 起分拣小票抬头带供应商来源，代分拣的货能直接把票交给供应商。
         */
        @Test
        void sortingTicketShowsSupplierFromV162() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 162) {
                return;
            }
            Integer tickets = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_print_template WHERE document_type = 'SORTING_TICKET' "
                            + "AND default_flag = TRUE AND (model -> 'headerFields') @> jsonb_build_array('supplierName')",
                    Integer.class);
            assertThat(tickets).as("分拣小票默认模板应包含供应商来源").isEqualTo(1);
        }

        /**
         * V163 起集团主体可显式授权子客户使用集团钱包结算，且只有 GROUP 客户能置真。
         */
        @Test
        void sharedSettlementGrantExistsFromV163() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 163) {
                return;
            }
            assertColumn("xsy_customer", "shared_settlement_enabled");
            Integer constraint = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_customer_shared_settlement_scope'",
                    Integer.class);
            assertThat(constraint).as("授权只能在 GROUP 结算客户上成立").isEqualTo(1);
        }

        /**
         * V164 起首页主题随草稿与发布版本一起保存，回滚时配色一起回去。
         */
        @Test
        void mallHomeThemeExistsFromV164() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 164) {
                return;
            }
            assertColumn("xsy_mall_home_draft", "theme_code");
            assertColumn("xsy_mall_home_release", "theme_code");
            Integer constraints = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE conname IN "
                            + "('ck_mall_home_draft_theme', 'ck_mall_home_release_theme')", Integer.class);
            assertThat(constraints).as("主题取值必须受控").isEqualTo(2);
        }

        /**
         * V165 起帮助中心按模块提供岗位操作说明；不含尚未提供的助手与语音入口。
         */
        @Test
        void helpDocOperationsExistFromV165() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 165) {
                return;
            }
            Integer docs = jdbc.queryForObject(
                    "SELECT count(*) FROM t_help_doc WHERE help_doc_id BETWEEN 7 AND 18", Integer.class);
            assertThat(docs).as("十二个模块各一篇操作说明").isEqualTo(12);
            Integer leaked = jdbc.queryForObject(
                    "SELECT count(*) FROM t_help_doc WHERE help_doc_id BETWEEN 7 AND 18 "
                            + "AND (title LIKE '%助手%' OR title LIKE '%语音%')", Integer.class);
            assertThat(leaked).as("不写尚未提供的功能入口").isZero();
        }

        /**
         * V166 起时价有独立审计表，且只能新增。
         */
        @Test
        void timePriceAuditExistsFromV166() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 166) {
                return;
            }
            String table = target >= 220 ? "xsy_price_operation_log" : "xsy_pricing_time_price_operation_log";
            String trigger = target >= 220 ? "trg_price_operation_log_append_only" : "trg_time_price_log_append_only";
            assertColumn(table, "before_data");
            Integer appendOnly = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_trigger WHERE tgname = '" + trigger + "' AND NOT tgisinternal",
                    Integer.class);
            assertThat(appendOnly).as("审计日志必须只能新增").isEqualTo(1);
        }

        /**
         * V167 起所有仍启用的分拣小票模板（含存量自定义模板）抬头都带供应商来源。
         */
        @Test
        void sortingTicketAllEnabledTemplatesHaveSupplierFromV167() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 167) {
                return;
            }
            Integer missing = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_print_template WHERE document_type = 'SORTING_TICKET' "
                            + "AND enabled_flag = TRUE "
                            + "AND NOT (model -> 'headerFields') @> jsonb_build_array('supplierName')",
                    Integer.class);
            assertThat(missing).as("存量自定义小票模板也应带供应商来源").isZero();
        }

        /** V181 起客户/供应商共用一张按 scope 隔离的小树，档案只存分类 id。 */
        @Test
        void businessCategoryExistsFromV181() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 181) {
                return;
            }
            assertColumn("xsy_business_category", "scope");
            assertColumn("xsy_business_category", "category_code");
            assertColumn("xsy_customer", "business_category_id");
            assertColumn("xsy_supplier", "business_category_id");
            Integer seeds = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_business_category WHERE deleted = FALSE "
                            + "AND scope IN ('CUSTOMER', 'SUPPLIER') AND name IN ('蔬菜', '蛋', '肉')",
                    Integer.class);
            assertThat(seeds).as("客户与供应商各三个初始分类").isEqualTo(6);
            Integer pageMenu = jdbc.queryForObject(
                    "SELECT count(*) FROM t_menu WHERE menu_id = 47604 AND menu_type = 2", Integer.class);
            assertThat(pageMenu).as("分类维护页面应随迁移发布").isEqualTo(1);
        }

        /** V182 起对账单可打印，客户专属模板按客户 + 单据类型唯一。 */
        @Test
        void statementPrintAndCustomerBindingExistFromV182() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 182) {
                return;
            }
            Integer statements = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_print_template WHERE document_type = 'STATEMENT' "
                            + "AND template_code = 'STATEMENT_DEFAULT' AND default_flag = TRUE "
                            + "AND enabled_flag = TRUE AND deleted = FALSE",
                    Integer.class);
            assertThat(statements).as("对账单默认模板应存在").isEqualTo(1);
            Integer constraints = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE connamespace = 'xsy'::regnamespace "
                            + "AND conname IN ('ck_scm_print_template_document_type', "
                            + "'ck_scm_print_record_document_type') "
                            + "AND pg_get_constraintdef(oid) LIKE '%STATEMENT%'",
                    Integer.class);
            assertThat(constraints).as("模板与打印记录都要允许对账单").isEqualTo(2);
            assertColumn("xsy_print_customer_template", "customer_id");
            Integer uniqueBinding = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy' "
                            + "AND indexname = 'uk_scm_print_customer_template'",
                    Integer.class);
            assertThat(uniqueBinding).as("同客户同单据类型只能有一条有效绑定").isEqualTo(1);
        }

        /** V183 起最近采购价按商品规格取最新已提交行，索引支撑 DISTINCT ON。 */
        @Test
        void purchaseLastPriceIndexesExistFromV183() {
            int target = Integer.parseInt(targetVersion().split("\\.")[0]);
            if (target < 183) {
                return;
            }
            Integer indexes = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy' "
                            + "AND indexname IN ('idx_purchase_order_item_sku_live', "
                            + "'idx_purchase_order_committed_submitted')",
                    Integer.class);
            assertThat(indexes).as("最近采购价查询索引应随迁移建立").isEqualTo(2);
        }

        private void assertColumn(String table, String column) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.columns "
                            + "WHERE table_schema = 'xsy' AND table_name = ? AND column_name = ?",
                    Integer.class, table, column);
            assertThat(count).as("%s.%s should exist", table, column).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("SCM 日期范围解析")
    class ScmDateTimeRangeResolverTest {
        @Test
        @DisplayName("业务日期转换为上海时区的半开区间")
        void resolvesBusinessDatesToHalfOpenRange() {
            LocalDate date = LocalDate.of(2026, 9, 29);

            ScmDateTimeRange range = ScmDateTimeRangeResolver.resolve(date, date);

            assertThat(range.startAt()).isEqualTo(OffsetDateTime.parse("2026-09-29T00:00:00+08:00"));
            assertThat(range.endAt()).isEqualTo(OffsetDateTime.parse("2026-09-30T00:00:00+08:00"));
        }

        @Test
        @DisplayName("366 天包含首尾日可查，367 天拒绝")
        void enforcesMaximumInclusiveSpan() {
            LocalDate start = LocalDate.of(2025, 9, 29);
            LocalDate end = LocalDate.of(2026, 9, 29);

            assertThat(ScmDateTimeRangeResolver.resolve(start, end).startDate()).isEqualTo(start);
            assertThatThrownBy(() -> ScmDateTimeRangeResolver.resolve(start.minusDays(1), end))
                    .isInstanceOf(ScmBusinessException.class);
        }

        @Test
        @DisplayName("缺失或倒序日期返回校验错误")
        void rejectsMissingOrReversedDates() {
            LocalDate today = LocalDate.of(2026, 9, 29);

            assertThatThrownBy(() -> ScmDateTimeRangeResolver.resolve(null, today))
                    .isInstanceOf(ScmBusinessException.class);
            assertThatThrownBy(() -> ScmDateTimeRangeResolver.resolve(today, today.minusDays(1)))
                    .isInstanceOf(ScmBusinessException.class);
        }
    }

    @Nested
    class CommonUnitTest3 {
        @Nested

        class ScmDecimalStringsTest {
            private static int codeOf(Throwable t) {
                return ((ScmBusinessException) t).getErrorCode().getCode();
            }

            @Test
            @DisplayName("接受 4 位以内小数的规范字符串")
            void acceptsCanonicalStrings() {
                assertThat(ScmDecimalStrings.parseScale4("12.3400")).isEqualByComparingTo(new BigDecimal("12.3400"));
                assertThat(ScmDecimalStrings.parseScale4("12.34")).isEqualByComparingTo(new BigDecimal("12.3400"));
                assertThat(ScmDecimalStrings.parseScale4("0")).isEqualByComparingTo(new BigDecimal("0.0000"));
                assertThat(ScmDecimalStrings.parseScale4("  8  ")).isEqualByComparingTo(new BigDecimal("8.0000"));
            }

            @Test
            @DisplayName("超过 4 位小数被拒绝，不做静默截断")
            void rejectsMoreThanFourDecimals() {
                assertThatThrownBy(() -> ScmDecimalStrings.parseScale4("12.34000"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
            }

            @Test
            @DisplayName("JSON 数字字面量被拒绝：数量与金额只以字符串传输")
            void rejectsNumericLiterals() {
                assertThatThrownBy(() -> ScmDecimalStrings.parseScale4(new BigDecimal("12.34")))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
                assertThatThrownBy(() -> ScmDecimalStrings.parseScale4(12.34d))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
                assertThatThrownBy(() -> ScmDecimalStrings.parseScale4(12))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
            }

            @Test
            @DisplayName("null 与空白都表示「无值」，与 0 严格区分")
            void nullAndBlankMeanAbsent() {
                assertThat(ScmDecimalStrings.parseScale4(null)).isNull();
                assertThat(ScmDecimalStrings.parseScale4("")).isNull();
                assertThat(ScmDecimalStrings.parseScale4("   ")).isNull();
                assertThat(ScmDecimalStrings.parseScale4("0")).isNotNull().isEqualByComparingTo("0.0000");
            }

            @Test
            @DisplayName("整数部分超过 14 位被拒绝")
            void rejectsTooManyIntegerDigits() {
                assertThat(ScmDecimalStrings.parseScale4("12345678901234")).isNotNull();
                assertThatThrownBy(() -> ScmDecimalStrings.parseScale4("123456789012345"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
            }

            @Test
            @DisplayName("负数与科学计数法被拒绝")
            void rejectsNegativeAndScientificNotation() {
                assertThatThrownBy(() -> ScmDecimalStrings.parseScale4("-1"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
                assertThatThrownBy(() -> ScmDecimalStrings.parseScale4("-0.0001"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
                assertThatThrownBy(() -> ScmDecimalStrings.parseScale4("1e5"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
                assertThatThrownBy(() -> ScmDecimalStrings.parseScale4("1E5"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
            }

            @Test
            @DisplayName("必填解析：空白与 null 都失败")
            void requiredRejectsAbsent() {
                assertThat(ScmDecimalStrings.parseScale4Required("1.5")).isEqualByComparingTo(new BigDecimal("1.5000"));
                assertThatThrownBy(() -> ScmDecimalStrings.parseScale4Required(null)).isInstanceOf(ScmBusinessException.class);
                assertThatThrownBy(() -> ScmDecimalStrings.parseScale4Required("  ")).isInstanceOf(ScmBusinessException.class);
            }

            @Test
            @DisplayName("输出形态与 ScmFixedScale4Serializer 对称")
            void formatMatchesSerializerContract() {
                assertThat(ScmDecimalStrings.format(null)).isNull();
                assertThat(ScmDecimalStrings.format(BigDecimal.ZERO)).isEqualTo("0.0000");
                assertThat(ScmDecimalStrings.format(new BigDecimal("12.34565"))).isEqualTo("12.3457");
                assertThat(ScmDecimalStrings.format(new BigDecimal("8"))).isEqualTo("8.0000");
            }

            @Test
            @DisplayName("isCanonical 与 @Pattern 使用同一字面量")
            void isCanonicalMatchesPatternConstant() {
                assertThat(ScmDecimalStrings.PATTERN).isEqualTo("^\\d{1,14}(\\.\\d{1,4})?$");
                assertThat(ScmDecimalStrings.isCanonical(null)).isTrue();
                assertThat(ScmDecimalStrings.isCanonical("")).isTrue();
                assertThat(ScmDecimalStrings.isCanonical("1.2345")).isTrue();
                assertThat(ScmDecimalStrings.isCanonical("1.23456")).isFalse();
                assertThat(ScmDecimalStrings.isCanonical("-1")).isFalse();
            }
        }

        @Nested
        @DisplayName("SCM 单据编号规则")
        class ScmDocumentNumbersTest {
            @Test
            @DisplayName("单号 = XSY + 雪花 ID")
            void documentNumberUsesXsyPrefixAndSnowflakeId() {
                assertThat(ScmDocumentNumbers.next()).matches("^XSY\\d{18,19}$");
            }

            @Test
            @DisplayName("连续取号不重复")
            void consecutiveNumbersAreUnique() {
                assertThat(ScmDocumentNumbers.next()).isNotEqualTo(ScmDocumentNumbers.next());
            }

            @Test
            @DisplayName("主数据编码 = 前缀 + 至少 6 位补零，不带日期段")
            void masterCodePadsWithoutDateSegment() {
                assertThat(ScmDocumentNumbers.masterCode("CUS", 1L)).isEqualTo("CUS000001");
                assertThat(ScmDocumentNumbers.masterCode("SUP", 123456L)).isEqualTo("SUP123456");
                assertThat(ScmDocumentNumbers.masterCode("WH", 1_000_000L)).isEqualTo("WH1000000");
            }
        }
    }
}
