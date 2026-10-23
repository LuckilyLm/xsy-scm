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
import com.xsy.scm.metrics.domain.SalesRangeMetrics;
import com.xsy.scm.metrics.domain.TrendMetrics;
import com.xsy.scm.metrics.domain.TrendSeriesSelection;
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
 * 首页指标统一从经营统计服务读取，并按当前数据权限展示；没有权限的指标隐藏，不用零值冒充。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScmDashboardService {

    /** 首页排行默认取前 5。 */
    public static final int HOME_RANK_LIMIT = 5;

    /** 首页与大屏共用指标层的排行上限。 */
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
     * 先按权限选出可见指标，再只查相关汇总。「没查」和「查了是零」必须区别对待。
     */
    public List<ScmDashboardCardVO> overviewFor(List<String> heldPermissions) {
        List<ScmDashboardCardEnum> visibleCards = new ArrayList<>();
        boolean needsSales = false;
        boolean needsPurchase = false;
        boolean needsWarning = false;
        for (ScmDashboardCardEnum card : ScmDashboardCardEnum.values()) {
            if (!card.visibleTo(heldPermissions)) {
                continue;
            }
            visibleCards.add(card);
            switch (card.group()) {
                case SALES -> needsSales = true;
                case PURCHASE -> needsPurchase = true;
                case INVENTORY_WARNING -> needsWarning = true;
            }
        }
        if (visibleCards.isEmpty()) {
            // 一张卡都不可见时连范围都不必解析：解析范围本身也是一次依赖上下文的调用。
            return List.of();
        }
        ScmDataScopeContext scope = dataScopeService.resolve();
        SalesRangeMetrics sales = needsSales ? metricsService.todaySalesRange(scope) : null;
        PurchaseMetrics purchase = needsPurchase ? metricsService.todayPurchase(scope) : null;
        long warningTotal = needsWarning ? warningTotal() : 0L;
        return cardsFor(visibleCards, sales, purchase, warningTotal);
    }

    /**
     * 按给定权限集合与指标计算卡片（与登录态解耦，便于负向夹具直接验证）。
     *
     * <p>
     * 只接受已经筛好的卡片集合，可见性判断不在这里重复做，避免两处判断结果不一致。
     */
    public List<ScmDashboardCardVO> cardsFor(List<ScmDashboardCardEnum> visibleCards, SalesRangeMetrics sales,
        PurchaseMetrics purchase, long warningTotal) {
        List<ScmDashboardCardVO> cards = new ArrayList<>(visibleCards.size());
        for (ScmDashboardCardEnum card : visibleCards) {
            cards.add(new ScmDashboardCardVO(card.getKey(), valueOf(card, sales, purchase, warningTotal),
                card.getUnit(), card.getRoute()));
        }
        return cards;
    }

    /**
     * 卡片类型逐一处理，新增卡片要明确数据来源。未查询的指标不能用零替代。
     */
    private static BigDecimal valueOf(ScmDashboardCardEnum card, SalesRangeMetrics sales, PurchaseMetrics purchase,
        long warningTotal) {
        return switch (card) {
            case SALES_AMOUNT -> require(sales, card).settlementAmount();
            case ORDER_COUNT -> BigDecimal.valueOf(require(sales, card).orderCount());
            case PURCHASE_AMOUNT -> require(purchase, card).todayPurchaseAmount();
            case RECEIPT_COUNT -> BigDecimal.valueOf(require(purchase, card).todayReceiptCount());
            case INVENTORY_WARNING -> BigDecimal.valueOf(warningTotal);
        };
    }

    /**
     * 卡片要求的那组聚合必须取过；取不到说明可见卡片与取数编排对不上，属于编码错误。
     */
    private static <T> T require(T metrics, ScmDashboardCardEnum card) {
        if (metrics == null) {
            throw new IllegalStateException("卡片 " + card.getKey() + " 可见，但其所属聚合未取数");
        }
        return metrics;
    }

    /**
     * 库存预警总数：与预警列表一致（同一个查询服务、同一套默认筛选），所以「卡片数字 = 点进去的条数」。
     *
     * <p>
     * 不在这里写统计 SQL，也不拿健康度分档求和：后者范围更宽（含缺货与未配置阈值），会让首页与明细对不上。
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
     * 与顶部的「库存预警」是两个指标：这里含缺货与未配置阈值，预警列表只收低于下限 / 高于上限。权限同样按库存预警权校验（看到分档等于看到库存状况）。
     */
    public ScmDashboardInventoryHealthVO inventoryHealth() {
        StpUtil.checkPermission(InventoryPermission.WARNING_QUERY);
        InventoryHealth health = metricsService.inventory(dataScopeService.resolve()).health();
        return new ScmDashboardInventoryHealthVO(health.totalSkuCount(), health.normalCount(), health.lowCount(),
            health.highCount(), health.unconfiguredCount(), health.outOfStockCount());
    }

    /**
     * 趋势：按指标族校验领域权限后再取数。
     */
    public ScmDashboardTrendVO trend(String metric, String range) {
        ScmDashboardTrendMetric target = ScmDashboardTrendMetric.parse(metric);
        requireQueryPermission(target);
        TrendSeriesSelection selection = switch (target) {
            case SALES -> TrendSeriesSelection.SALES;
            case PURCHASE -> TrendSeriesSelection.PURCHASE;
            case INVENTORY -> TrendSeriesSelection.INVENTORY;
        };
        TrendMetrics trend = metricsService.trend(range, dataScopeService.resolve(), selection);
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
     * 入口权限 {@code scm:dashboard:query} 只授权访问工作台本身；不逐个校验的话，拿到入口权限就能顺着 {@code metric} 参数拉出订单、采购、库存的曲线。
     */
    private static void requireQueryPermission(ScmDashboardTrendMetric metric) {
        switch (metric) {
            case SALES -> StpUtil.checkPermission(OrderPermission.QUERY);
            case PURCHASE -> StpUtil.checkPermission(PurchasePermission.QUERY);
            case INVENTORY -> StpUtil.checkPermission(InventoryPermission.MOVEMENT_QUERY);
        }
    }

    /**
     * 排行仅查询所选维度，数据范围与销售指标一致。
     */
    public List<RankItem> ranking(String dimension, Integer limit) {
        ScmDashboardRankDimension target = ScmDashboardRankDimension.parse(dimension);
        requireQueryPermission(target);
        ScmDataScopeContext scope = dataScopeService.resolve();
        int requestedLimit = rankLimit(limit);
        return switch (target) {
            case CUSTOMER -> metricsService.todayTopCustomers(requestedLimit, scope);
            case PRODUCT -> metricsService.todayTopProducts(requestedLimit, scope);
        };
    }

    /**
     * 两个排行维度都来自销售数据，因此都需要订单查询权。
     */
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
