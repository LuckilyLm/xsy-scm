package com.xsy.scm.dashboard.service;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.dashboard.domain.vo.ScmDashboardCardVO;
import com.xsy.scm.dashboard.domain.vo.ScmDashboardInventoryHealthVO;
import com.xsy.scm.inventory.permission.InventoryPermission;
import com.xsy.scm.inventory.service.InventoryWarningQueryService;
import com.xsy.scm.metrics.domain.InventoryHealth;
import com.xsy.scm.metrics.domain.InventoryMetrics;
import com.xsy.scm.metrics.domain.PurchaseMetrics;
import com.xsy.scm.metrics.domain.SalesMetrics;
import com.xsy.scm.metrics.service.ScmBusinessMetricsService;
import net.lab1024.sa.admin.module.system.login.manager.LoginManager;
import net.lab1024.sa.base.common.domain.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作台卡片口径与门禁的纯逻辑单测（无库）。
 *
 * <p>钉住两件事：<b>库存预警卡片的数字来自预警列表本身</b>（不是指标层那张更宽的健康度分档求和，
 * 否则会出现「首页 12、点进去只有 8」），以及逐卡裁剪与库存健康度的权限边界。
 */
@DisplayName("工作台卡片口径与门禁（unit）")
class ScmDashboardServiceTest {

    private final LoginManager loginManager = mock(LoginManager.class);
    private final ScmBusinessMetricsService metrics = mock(ScmBusinessMetricsService.class);
    private final ScmDataScopeService scopeService = mock(ScmDataScopeService.class);
    private final InventoryWarningQueryService warning = mock(InventoryWarningQueryService.class);

    private final ScmDashboardService service =
            new ScmDashboardService(loginManager, metrics, scopeService, warning);

    @BeforeEach
    void stubMetrics() {
        when(metrics.todaySales(any())).thenReturn(
                new SalesMetrics(3L, new BigDecimal("100.0000"), new BigDecimal("120.0000"), 30L,
                        new BigDecimal("1000.0000"), 2L, List.of(), List.of()));
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
