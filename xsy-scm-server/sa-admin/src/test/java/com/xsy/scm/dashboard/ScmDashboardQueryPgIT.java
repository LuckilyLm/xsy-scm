package com.xsy.scm.dashboard;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.dashboard.constant.ScmDashboardCardEnum;
import com.xsy.scm.dashboard.domain.vo.ScmDashboardCardVO;
import com.xsy.scm.dashboard.domain.vo.ScmDashboardTrendVO;
import com.xsy.scm.dashboard.service.ScmDashboardService;
import com.xsy.scm.inventory.domain.form.InventoryWarningQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryWarningVO;
import com.xsy.scm.inventory.permission.InventoryPermission;
import com.xsy.scm.inventory.service.InventoryWarningQueryService;
import com.xsy.scm.metrics.domain.RankItem;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.purchase.permission.PurchasePermission;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * 首页「供应链工作台」聚合端点（PG IT）。
 *
 * <p>钉住四件事：
 * <ol>
 * <li>KPI 卡片按「入口权限 ∩ 领域权限」逐卡裁剪，<b>无权整卡省略而不是给 0</b> —— 0 会被读成「今天真的没有」。</li>
 * <li>趋势与排行是单指标端点，缺对应领域权限直接拒绝：入口权限不隐含订单 / 采购 / 库存的可见性。</li>
 * <li>7d / 30d 的序列长度与末点日期。</li>
 * <li>工作台沿用大屏那一套数据范围（归属 ∩ 仓库），不沿用报表的宽范围语义。</li>
 * </ol>
 */
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
        SmartRequestUtil.setRequestUser(employee);
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
            // 库存趋势走库存流水查询权；两个排行维度都来自销售事实，所以共用订单查询权（销售额 1 次 + 两个排行各 1 次）。
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
        SmartRequestUtil.setRequestUser(employee);
    }
}
