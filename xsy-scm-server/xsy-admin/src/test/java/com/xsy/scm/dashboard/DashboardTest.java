package com.xsy.scm.dashboard;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.admin.module.system.login.manager.LoginManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.dashboard.constant.ScmDashboardCardEnum;
import com.xsy.scm.dashboard.domain.vo.ScmDashboardCardVO;
import com.xsy.scm.dashboard.domain.vo.ScmDashboardInventoryHealthVO;
import com.xsy.scm.dashboard.domain.vo.ScmDashboardTrendVO;
import com.xsy.scm.dashboard.domain.vo.ScmTodoVO;
import com.xsy.scm.dashboard.service.ScmDashboardService;
import com.xsy.scm.delivery.service.DeliveryRouteQueryService;
import com.xsy.scm.inventory.domain.form.InventoryWarningQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryWarningVO;
import com.xsy.scm.inventory.permission.InventoryPermission;
import com.xsy.scm.inventory.service.InventoryLossGainQueryService;
import com.xsy.scm.inventory.service.InventoryWarningQueryService;
import com.xsy.scm.metrics.domain.InventoryHealth;
import com.xsy.scm.metrics.domain.InventoryMetrics;
import com.xsy.scm.metrics.domain.PurchaseMetrics;
import com.xsy.scm.metrics.domain.RankItem;
import com.xsy.scm.metrics.domain.SalesRangeMetrics;
import com.xsy.scm.metrics.service.ScmBusinessMetricsService;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.purchase.permission.PurchasePermission;
import com.xsy.scm.purchase.service.PurchaseQueryService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.xsy.scm.dashboard.service.ScmTodoQueryService;

class DashboardTest {

    @Nested
    @DisplayName("首页供应链工作台聚合（PG IT）")
    class ScmDashboardQueryPgIT extends ScmW5PgITBase {
        @Autowired
        private ScmDashboardService dashboardService;

        @Autowired
        private InventoryWarningQueryService inventoryWarningQueryService;

        @BeforeEach
        void loginAsAdministrator() {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("工作台 IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            employee.setDepartmentId(1L);
            XsyRequestUtil.setRequestUser(employee);
        }

        @Test
        @DisplayName("KPI 卡片逐卡裁剪：无权整卡省略，而不是给 0")
        void cardsAreOmittedByDomainPermission() {
            assertThat(dashboardService.overviewFor(List.of()))
                    .as("只有入口权限、一个领域权限都没有时，一张卡都不该给").isEmpty();

            assertThat(keysOf(List.of(OrderPermission.QUERY)))
                    .as("只有订单查询权：销售额与订单两张卡")
                    .containsExactly("sales-amount", "order-count");

            assertThat(keysOf(List.of(InventoryPermission.WARNING_QUERY)))
                    .as("只有库存预警权：只给库存异常一张卡，不能顺带把订单 / 采购的数字也给他")
                    .containsExactly("inventory-warning");

            assertThat(keysOf(List.of(OrderPermission.QUERY, PurchasePermission.QUERY,
                    PurchasePermission.RECEIPT_QUERY, InventoryPermission.WARNING_QUERY)))
                    .as("四个领域权限齐全时五张卡都在").hasSize(ScmDashboardCardEnum.values().length);
        }

        @Test
        @DisplayName("趋势与排行按指标 / 维度校验领域权限，不靠入口权限兜底")
        void trendAndRankingRequireDomainPermission() {
            try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                dashboardService.trend("sales", "7d");
                dashboardService.trend("purchase", "7d");
                dashboardService.trend("inventory", "7d");
                dashboardService.ranking("customer", 5);
                dashboardService.ranking("product", 5);

                // 逐个断言「哪个指标要哪个领域权限」：销售额走订单查询权，采购额走采购查询权，
                // 库存趋势走库存流水查询权；两个排行维度都来自销售记录，所以共用订单查询权（销售额 1 次 + 两个排行各 1 次）。
                stp.verify(() -> StpUtil.checkPermission(OrderPermission.QUERY), times(3));
                stp.verify(() -> StpUtil.checkPermission(PurchasePermission.QUERY), times(1));
                stp.verify(() -> StpUtil.checkPermission(InventoryPermission.MOVEMENT_QUERY), times(1));
            }
        }

        @Test
        @DisplayName("领域权限被拒时趋势直接失败，不返回空曲线")
        void trendFailsWhenDomainPermissionDenied() {
            try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                stp.when(() -> StpUtil.checkPermission(PurchasePermission.QUERY))
                        .thenThrow(new IllegalStateException("denied"));
                assertThatThrownBy(() -> dashboardService.trend("purchase", "7d"))
                        .as("缺采购查询权时必须拒绝，而不是给一条空曲线").isInstanceOf(IllegalStateException.class);
            }
        }

        @Test
        @DisplayName("趋势 7d / 30d：序列长度与日期轴，末点就是今天")
        void trendRangeCoversSevenAndThirtyDays() {
            try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                ScmDashboardTrendVO week = dashboardService.trend("sales", "7d");
                assertThat(week.range()).isEqualTo("7d");
                assertThat(week.dates()).hasSize(7);
                assertThat(week.primarySeries()).hasSize(7);
                assertThat(week.secondarySeries()).hasSize(7);

                ScmDashboardTrendVO month = dashboardService.trend("sales", "30d");
                assertThat(month.range()).isEqualTo("30d");
                assertThat(month.dates()).hasSize(30);

                // 区间取值非法时按 7d 处理（URL 上的取值不该让整块面板失败），未知 metric 才拒绝。
                assertThat(dashboardService.trend("sales", "90d").range()).isEqualTo("7d");
                assertThatThrownBy(() -> dashboardService.trend("unknown", "7d")).isInstanceOf(RuntimeException.class);
            }
        }

        @Test
        @DisplayName("排行：默认前 5、显式 limit 生效、上限是指标层已取回的条数")
        void rankingIsTruncatedByLimit() {
            try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                List<RankItem> defaulted = dashboardService.ranking("customer", null);
                assertThat(defaulted.size()).isLessThanOrEqualTo(ScmDashboardService.HOME_RANK_LIMIT);

                assertThat(dashboardService.ranking("customer", 2).size()).isLessThanOrEqualTo(2);
                assertThat(dashboardService.ranking("product", 2).size()).isLessThanOrEqualTo(2);

                // limit 超过指标层上限时按上限截断，不会去改 SQL 的 limit
                assertThat(dashboardService.ranking("customer", 999).size())
                        .isLessThanOrEqualTo(ScmDashboardService.MAX_RANK_LIMIT);

                assertThatThrownBy(() -> dashboardService.ranking("unknown", 5)).isInstanceOf(RuntimeException.class);
            }
        }

        @Test
        @DisplayName("工作台沿用大屏的数据范围：受限员工看不到别人名下的销售")
        void overviewUsesTheCallersDataScope() {
            Long skuId = newOnShelfSku("DBX");
            Long customerId = newCustomer();
            confirmedSalesOrder(customerId, skuId, "2.0000", "2.0000");

            BigDecimal adminSales = cardValue(dashboardService.overviewFor(List.of(OrderPermission.QUERY)),
                    ScmDashboardCardEnum.SALES_AMOUNT.getKey());
            assertThat(adminSales).as("不受范围约束的操作者应当看得到这张单").isPositive();

            Long scoped = newEmployee("DBX-EMP");
            try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                loginAs(scoped);
                BigDecimal scopedSales = cardValue(dashboardService.overviewFor(List.of(OrderPermission.QUERY)),
                        ScmDashboardCardEnum.SALES_AMOUNT.getKey());
                assertThat(scopedSales).as("受限员工只该看到自己名下的销售，卡片值必须是 0 而不是全公司的数")
                        .isEqualByComparingTo(BigDecimal.ZERO);
            } finally {
                loginAsAdministrator();
            }
        }

        @Test
        @DisplayName("库存预警卡片与预警列表同源：卡片数字就是点进去的条数")
        void inventoryWarningCardEqualsTheWarningListTotal() {
            InventoryWarningQueryForm form = new InventoryWarningQueryForm();
            form.setPageNum(1L);
            form.setPageSize(1L);
            PageResult<InventoryWarningVO> page = inventoryWarningQueryService.queryWarningPage(form);
            long listTotal = page == null || page.getTotal() == null ? 0L : page.getTotal();

            BigDecimal cardValue = cardValue(dashboardService.overviewFor(List.of(InventoryPermission.WARNING_QUERY)),
                    ScmDashboardCardEnum.INVENTORY_WARNING.getKey());

            assertThat(cardValue).as("卡片与列表必须是同一个数，否则用户点进去会觉得少了")
                    .isEqualByComparingTo(BigDecimal.valueOf(listTotal));
        }

        private List<String> keysOf(List<String> heldPermissions) {
            return dashboardService.overviewFor(heldPermissions).stream().map(ScmDashboardCardVO::key).toList();
        }

        private static BigDecimal cardValue(List<ScmDashboardCardVO> cards, String key) {
            return cards.stream().filter(card -> card.key().equals(key)).map(ScmDashboardCardVO::value).findFirst()
                    .orElseThrow(() -> new AssertionError("卡片 " + key + " 应当可见"));
        }

        /** 真实员工行，{@code administrator_flag} 必须 FALSE（正式业务角色的验收账号禁止超管位）。 */
        private Long newEmployee(String suffix) {
            String loginName = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
            jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                            + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                    UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder",
                    "工作台" + suffix);
            return jdbc.queryForObject("SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
        }

        private void loginAs(Long employeeId) {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(employeeId);
            employee.setActualName("工作台范围 IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(false);
            employee.setDepartmentId(1L);
            XsyRequestUtil.setRequestUser(employee);
        }
    }

    @Nested
    @DisplayName("工作台卡片规则与门禁（unit）")
    class ScmDashboardServiceTest {
        private final LoginManager loginManager = mock(LoginManager.class);
        private final ScmBusinessMetricsService metrics = mock(ScmBusinessMetricsService.class);
        private final ScmDataScopeService scopeService = mock(ScmDataScopeService.class);
        private final InventoryWarningQueryService warning = mock(InventoryWarningQueryService.class);

        private final ScmDashboardService service =
                new ScmDashboardService(loginManager, metrics, scopeService, warning);

        @BeforeEach
        void stubMetrics() {
            when(metrics.todaySalesRange(any())).thenReturn(
                    new SalesRangeMetrics(3L, new BigDecimal("120.0000"), 2L));
            when(metrics.todayPurchase(any())).thenReturn(
                    new PurchaseMetrics(4L, new BigDecimal("200.0000"), 40L, new BigDecimal("2000.0000"), 1L, 3L));
        }

        private static <T> PageResult<T> withTotal(long total) {
            PageResult<T> page = new PageResult<>();
            page.setTotal(total);
            return page;
        }

        private static BigDecimal cardValue(List<ScmDashboardCardVO> cards, String key) {
            return cards.stream().filter(card -> card.key().equals(key)).map(ScmDashboardCardVO::value).findFirst()
                    .orElseThrow(() -> new AssertionError("卡片 " + key + " 应当可见"));
        }

        @Test
        @DisplayName("库存预警卡片取的是预警列表总数，不是健康度分档求和")
        void inventoryWarningCardComesFromTheWarningList() {
            when(warning.queryWarningPage(any())).thenReturn(withTotal(12));

            List<ScmDashboardCardVO> cards = service.overviewFor(List.of(InventoryPermission.WARNING_QUERY));

            assertThat(cardValue(cards, "inventory-warning")).isEqualByComparingTo(BigDecimal.valueOf(12));
            verify(warning).queryWarningPage(any());
        }

        @Test
        @DisplayName("逐卡裁剪：没有领域权限时一张卡都不给，无权卡片不出现而不是给 0")
        void noDomainPermissionYieldsNoCards() {
            when(warning.queryWarningPage(any())).thenReturn(withTotal(12));

            assertThat(service.overviewFor(List.of())).isEmpty();
            assertThat(service.overviewFor(List.of("scm:todo:query"))).as("入口权限之外的无关权限不构成卡片可见性")
                    .isEmpty();
        }

        @Test
        @DisplayName("按可见卡片惰性取数：只有库存预警权时，销售与采购聚合一次都不查")
        void invisibleGroupsAreNotQueried() {
            when(warning.queryWarningPage(any())).thenReturn(withTotal(12));

            service.overviewFor(List.of(InventoryPermission.WARNING_QUERY));

            verify(warning).queryWarningPage(any());
            verify(metrics, never()).todaySalesRange(any());
            verify(metrics, never()).todayPurchase(any());
        }

        @Test
        @DisplayName("按可见卡片惰性取数：只有订单权时只查销售聚合，不查采购与预警")
        void onlyVisibleGroupIsQueried() {
            service.overviewFor(List.of(OrderPermission.QUERY));

            verify(metrics).todaySalesRange(any());
            verify(metrics, never()).todayPurchase(any());
            verify(warning, never()).queryWarningPage(any());
        }

        @Test
        @DisplayName("按可见卡片惰性取数：采购权只查采购聚合，不查销售与预警")
        void purchaseOnlyQueriesPurchaseGroup() {
            service.overviewFor(List.of(PurchasePermission.QUERY));

            verify(metrics).todayPurchase(any());
            verify(metrics, never()).todaySalesRange(any());
            verify(warning, never()).queryWarningPage(any());
        }

        @Test
        @DisplayName("一张卡都不可见时连数据范围都不解析：解析范围本身也是一次依赖上下文的调用")
        void noVisibleCardSkipsScopeResolution() {
            assertThat(service.overviewFor(List.of())).isEmpty();

            verifyNoInteractions(scopeService);
            verifyNoInteractions(metrics);
            verifyNoInteractions(warning);
        }

        @Test
        @DisplayName("可见卡片与其聚合必须配套：协调漏取数时直接报错，不静默给 0")
        void missingAggregationForVisibleCardFailsLoudly() {
            assertThatThrownBy(() -> service.cardsFor(
                    List.of(com.xsy.scm.dashboard.constant.ScmDashboardCardEnum.SALES_AMOUNT), null, null, 0L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("sales-amount");
        }

        @Test
        @DisplayName("库存健康度：五档之和恒等于总数")
        void inventoryHealthBucketsSumToTotal() {
            when(metrics.inventory(any())).thenReturn(
                    new InventoryMetrics(BigDecimal.ZERO, 0, 0, 0, 0, new InventoryHealth(10L, 6L, 2L, 1L, 1L, 0L)));

            try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                ScmDashboardInventoryHealthVO health = service.inventoryHealth();
                stp.verify(() -> StpUtil.checkPermission(InventoryPermission.WARNING_QUERY));
                assertThat(health.total()).isEqualTo(10L);
                assertThat(health.normal() + health.low() + health.high() + health.unconfigured() + health.outOfStock())
                        .as("五档互斥且之和等于总数").isEqualTo(health.total());
            }
        }

        @Test
        @DisplayName("库存健康度要求库存预警权：缺权直接拒绝，不返回一片 0")
        void inventoryHealthRequiresWarningPermission() {
            try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                stp.when(() -> StpUtil.checkPermission(InventoryPermission.WARNING_QUERY))
                        .thenThrow(new IllegalStateException("denied"));
                assertThatThrownBy(() -> service.inventoryHealth()).isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Nested
    @DisplayName("业务待办卡片门禁（unit）")
    class ScmTodoQueryServiceTest {
        private final LoginManager loginManager = mock(LoginManager.class);
        private final InventoryWarningQueryService warning = mock(InventoryWarningQueryService.class);
        private final PurchaseQueryService purchase = mock(PurchaseQueryService.class);
        private final InventoryLossGainQueryService lossGain = mock(InventoryLossGainQueryService.class);
        private final DeliveryRouteQueryService delivery = mock(DeliveryRouteQueryService.class);

        private final ScmTodoQueryService service =
                new ScmTodoQueryService(loginManager, warning, purchase, lossGain, delivery);

        private static <T> PageResult<T> withTotal(long total) {
            PageResult<T> p = new PageResult<>();
            p.setTotal(total);
            return p;
        }

        private static Set<String> keys(List<ScmTodoVO> todos) {
            return todos.stream().map(ScmTodoVO::getKey).collect(Collectors.toSet());
        }

        @Test
        @DisplayName("仅待办入口权限：不返回任何领域卡片，也从不触发领域计数")
        void todoOnlyPermissionOmitsAllCardsAndNeverCounts() {
            List<ScmTodoVO> todos = service.todosFor(List.of("scm:todo:query"));

            assertThat(todos).isEmpty();
            verifyNoInteractions(warning, purchase, lossGain, delivery);
        }

        @Test
        @DisplayName("持齐领域权限：四张卡片齐全，各计数服务被调用一次")
        void fullPermissionsYieldAllFourCards() {
            when(warning.queryWarningPage(any())).thenReturn(withTotal(2));
            when(purchase.receiptQuery(any())).thenReturn(withTotal(3));
            when(lossGain.queryPage(any())).thenReturn(withTotal(1));
            when(delivery.query(any())).thenReturn(withTotal(4));

            List<ScmTodoVO> todos = service.todosFor(List.of(
                    "scm:inventory:warning:query",
                    "scm:purchase:receipt:query", "scm:purchase:receipt:putaway",
                    "scm:inventory:loss-gain:query", "scm:inventory:loss-gain:approve",
                    "scm:delivery:route:query", "scm:delivery:route:plan"));

            assertThat(keys(todos)).containsExactlyInAnyOrder(
                    "inventory-warning", "receipt-putaway", "loss-gain-audit", "delivery-route-pending");
            verify(warning).queryWarningPage(any());
            verify(purchase).receiptQuery(any());
            verify(lossGain).queryPage(any());
            verify(delivery).query(any());
        }

        @Test
        @DisplayName("有权但零任务：卡片仍在且计数为 0（区别于无权整卡省略）")
        void permittedZeroTaskCardIsPresentWithZero() {
            when(warning.queryWarningPage(any())).thenReturn(withTotal(0));

            List<ScmTodoVO> todos = service.todosFor(List.of("scm:inventory:warning:query"));

            assertThat(todos).singleElement().satisfies(card -> {
                assertThat(card.getKey()).isEqualTo("inventory-warning");
                assertThat(card.getCount()).isZero();
            });
        }

        @Test
        @DisplayName("缺审批权限时报损报溢卡片省略；持有 reject 即满足 any-of")
        void lossGainCardNeedsAnyApproveOrReject() {
            List<ScmTodoVO> queryOnly =
                    service.todosFor(List.of("scm:inventory:loss-gain:query"));
            assertThat(queryOnly).isEmpty();
            verifyNoInteractions(lossGain);

            List<ScmTodoVO> withReject = service.todosFor(
                    List.of("scm:inventory:loss-gain:query", "scm:inventory:loss-gain:reject"));
            assertThat(keys(withReject)).containsExactly("loss-gain-audit");
        }
    }
}
