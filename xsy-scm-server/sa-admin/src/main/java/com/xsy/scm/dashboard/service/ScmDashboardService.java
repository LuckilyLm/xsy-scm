package com.xsy.scm.dashboard.service;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.dashboard.constant.ScmDashboardCardEnum;
import com.xsy.scm.dashboard.constant.ScmDashboardRankDimension;
import com.xsy.scm.dashboard.constant.ScmDashboardTrendMetric;
import com.xsy.scm.dashboard.domain.vo.ScmDashboardCardVO;
import com.xsy.scm.dashboard.domain.vo.ScmDashboardInventoryHealthVO;
import com.xsy.scm.dashboard.domain.vo.ScmDashboardTrendVO;
import com.xsy.scm.inventory.domain.form.InventoryWarningQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryWarningVO;
import com.xsy.scm.inventory.permission.InventoryPermission;
import com.xsy.scm.inventory.service.InventoryWarningQueryService;
import com.xsy.scm.metrics.domain.InventoryHealth;
import com.xsy.scm.metrics.domain.PurchaseMetrics;
import com.xsy.scm.metrics.domain.RankItem;
import com.xsy.scm.metrics.domain.SalesMetrics;
import com.xsy.scm.metrics.domain.TrendMetrics;
import com.xsy.scm.metrics.service.ScmBusinessMetricsService;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.purchase.permission.PurchasePermission;
import net.lab1024.sa.admin.module.system.login.manager.LoginManager;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.domain.UserPermission;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页「供应链工作台」的只读聚合（不写任何业务表、不落快照）。
 *
 * <p>
 * <b>本类不写统计 SQL</b>：所有数字向 {@link ScmBusinessMetricsService} 取，口径与首页 / 大屏同一套 （销售看确认、采购看提交、收货看确认），数据范围也沿用大屏那一套「归属 ∩ 仓库」——
 * 不沿用报表的宽范围语义（见 {@code home-workbench-design.md} 的「口径同源 ≠ 范围同源」）。
 *
 * <p>
 * <b>可见性是两层</b>：入口权限 {@code scm:dashboard:query} 由控制器保证；每张卡片再过自己的领域权限， 无权卡片整卡省略、既不给数字也不填 0 —— 0
 * 会被读成「今天真的没有」。趋势与排行是单指标端点， 没有「省略」的形态，缺领域权限直接按无权限拒绝。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScmDashboardService {

    /** 首页排行默认取前 5。 */
    public static final int HOME_RANK_LIMIT = 5;

    /** 排行上限：metrics 已经取回 TOP10，这里只截断，不为了省几行去改 SQL 的 limit。 */
    public static final int MAX_RANK_LIMIT = ScmBusinessMetricsService.TOP_RANK_LIMIT;

    private final LoginManager loginManager;

    private final ScmBusinessMetricsService metricsService;

    private final ScmDataScopeService dataScopeService;

    private final InventoryWarningQueryService inventoryWarningQueryService;

    /** 当前登录员工的 KPI 卡片。 */
    public List<ScmDashboardCardVO> overview() {
        return overviewFor(heldPermissions());
    }

    /**
     * 按给定权限集合算卡片（与登录态解耦，便于负向夹具直接验证「省略」与「范围」的组合）。
     *
     * <p>
     * 三组数据一次取齐：它们是同一批走索引的聚合，比按卡片分组惰性取数少一层分支；代价是只有一种卡片权限的人 也会触发另外几组查询（都已按范围收窄，不构成越权）。要优化就按可见卡片惰性取数，见设计文档的待办项。
     */
    public List<ScmDashboardCardVO> overviewFor(List<String> heldPermissions) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        return cardsFor(heldPermissions, metricsService.todaySales(scope), metricsService.todayPurchase(scope),
                warningTotal());
    }

    /**
     * 按给定权限集合与指标计算卡片（与登录态解耦，便于负向夹具直接验证省略语义）。
     */
    public List<ScmDashboardCardVO> cardsFor(List<String> heldPermissions, SalesMetrics sales, PurchaseMetrics purchase,
            long warningTotal) {
        List<ScmDashboardCardVO> cards = new ArrayList<>();
        for (ScmDashboardCardEnum card : ScmDashboardCardEnum.values()) {
            if (!card.visibleTo(heldPermissions)) {
                continue;
            }
            cards.add(new ScmDashboardCardVO(card.getKey(), valueOf(card, sales, purchase, warningTotal),
                    card.getUnit(), card.getRoute()));
        }
        return cards;
    }

    /**
     * 卡片取值：穷尽 switch、不写 default —— 新增卡片时这里会直接编译不过，逼着补上取值口径。
     *
     * <p>
     * 三组数据一定非 null：调用方只为可见卡片取数，见 {@link #cardsFor}。
     */
    private static BigDecimal valueOf(ScmDashboardCardEnum card, SalesMetrics sales, PurchaseMetrics purchase,
            long warningTotal) {
        return switch (card) {
            case SALES_AMOUNT -> sales.todaySettlementAmount();
            case ORDER_COUNT -> BigDecimal.valueOf(sales.todayOrderCount());
            case PURCHASE_AMOUNT -> purchase.todayPurchaseAmount();
            case RECEIPT_COUNT -> BigDecimal.valueOf(purchase.todayReceiptCount());
            case INVENTORY_WARNING -> BigDecimal.valueOf(warningTotal);
        };
    }

    /**
     * 库存预警总数：与库存预警列表同源（同一个查询服务、同一套默认筛选），因此「卡片数字 = 点进去的条数」。
     *
     * <p>
     * 不在这里写统计 SQL，也不把指标层的健康度分档拿来求和 —— 后者的口径更宽（含缺货与未配置阈值）， 会让首页与明细对不上。
     */
    private long warningTotal() {
        InventoryWarningQueryForm form = new InventoryWarningQueryForm();
        form.setPageNum(1L);
        form.setPageSize(1L);
        PageResult<InventoryWarningVO> page = inventoryWarningQueryService.queryWarningPage(form);
        return page == null || page.getTotal() == null ? 0L : page.getTotal();
    }

    /**
     * 库存健康度五档，供首页的库存健康卡使用。
     *
     * <p>
     * 与顶部的「库存预警」是<b>两个指标</b>：这里含缺货与未配置阈值，预警列表只收低于下限 / 高于上限。 权限同样按库存预警权校验（看到分档等于看到库存状况）。
     */
    public ScmDashboardInventoryHealthVO inventoryHealth() {
        StpUtil.checkPermission(InventoryPermission.WARNING_QUERY);
        InventoryHealth health = metricsService.inventory(dataScopeService.resolve()).health();
        return new ScmDashboardInventoryHealthVO(health.totalSkuCount(), health.normalCount(), health.lowCount(),
                health.highCount(), health.unconfiguredCount(), health.outOfStockCount());
    }

    /** 趋势：按指标族校验领域权限后再取数。 */
    public ScmDashboardTrendVO trend(String metric, String range) {
        ScmDashboardTrendMetric target = ScmDashboardTrendMetric.parse(metric);
        requireQueryPermission(target);
        TrendMetrics trend = metricsService.trend(range, dataScopeService.resolve());
        return switch (target) {
            case SALES -> new ScmDashboardTrendVO(target.getCode(), trend.range(), trend.dates(), trend.sales(),
                    toAmounts(trend.orders()));
            case PURCHASE -> new ScmDashboardTrendVO(target.getCode(), trend.range(), trend.dates(),
                    trend.purchaseAmounts(), toAmounts(trend.purchaseOrders()));
            case INVENTORY -> new ScmDashboardTrendVO(target.getCode(), trend.range(), trend.dates(),
                    trend.inboundQuantity(), trend.outboundQuantity());
        };
    }

    /**
     * 逐个指标校验领域权限。
     *
     * <p>
     * 入口权限 {@code scm:dashboard:query} 只授权访问工作台本身；不在这里逐个校验，拿到入口权限的人就能顺着 {@code metric} 参数把订单、采购、库存的曲线都拉出来。
     */
    private static void requireQueryPermission(ScmDashboardTrendMetric metric) {
        switch (metric) {
            case SALES -> StpUtil.checkPermission(OrderPermission.QUERY);
            case PURCHASE -> StpUtil.checkPermission(PurchasePermission.QUERY);
            case INVENTORY -> StpUtil.checkPermission(InventoryPermission.MOVEMENT_QUERY);
        }
    }

    /** 排行：按维度校验领域权限后再取数，limit 由调用方给，上限就是 metrics 已经取回的条数。 */
    public List<RankItem> ranking(String dimension, Integer limit) {
        ScmDashboardRankDimension target = ScmDashboardRankDimension.parse(dimension);
        requireQueryPermission(target);
        SalesMetrics sales = metricsService.todaySales(dataScopeService.resolve());
        List<RankItem> source = switch (target) {
            case CUSTOMER -> sales.topCustomers();
            case PRODUCT -> sales.topProducts();
        };
        return source.stream().limit(rankLimit(limit)).toList();
    }

    /** 两个排行维度都来自销售事实，因此都需要订单查询权。 */
    private static void requireQueryPermission(ScmDashboardRankDimension dimension) {
        switch (dimension) {
            case CUSTOMER, PRODUCT -> StpUtil.checkPermission(OrderPermission.QUERY);
        }
    }

    private static int rankLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return HOME_RANK_LIMIT;
        }
        return Math.min(limit, MAX_RANK_LIMIT);
    }

    private static List<BigDecimal> toAmounts(List<Long> counts) {
        return counts.stream().map(BigDecimal::valueOf).toList();
    }

    private List<String> heldPermissions() {
        RequestUser user = SmartRequestUtil.getRequestUser();
        if (user == null || user.getUserId() == null) {
            return List.of();
        }
        UserPermission permission = loginManager.getUserPermission(user.getUserId());
        if (permission == null || permission.getPermissionList() == null) {
            return List.of();
        }
        return permission.getPermissionList();
    }
}
