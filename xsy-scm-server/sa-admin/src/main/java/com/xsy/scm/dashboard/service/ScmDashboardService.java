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
     * 按给定权限集合算卡片（与登录态解耦，便于负向夹具直接验证「省略」与「范围」的组合）。
     *
     * <p>
     * <b>只为可见卡片取数</b>：先按权限筛出这批人要看的卡片，再回头判断「哪几组聚合真的需要」。 三组聚合各自独立，谁都不依赖谁，所以能给多少给多少 —— 一个只有库存预警权的人 不该为销售额与采购额付两次查询的钱。
     *
     * <p>
     * 用 {@code null} 表示「本组聚合不需要」：它能与「取了但结果为空」区分开。 早先的写法是三组无条件取齐（代价写在注释里、留作待办），现在按待办收掉。
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
     * 按给定权限集合与指标计算卡片（与登录态解耦，便于负向夹具直接验证省略语义）。
     *
     * <p>
     * 只接受<b>已经筛好</b>的卡片集合，可见性判断不在这里重复做一遍 —— 两次判断就有两处可能漂移。
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
     * 卡片取值：穷尽 switch、不写 default —— 新增卡片时这里会直接编译不过，逼着补上取值口径。
     *
     * <p>
     * 某组聚合为 {@code null} 表示「没有卡片用到它」（调用方已按可见卡片裁剪）。 真被用到却是 null 属于调用方的编排错误，直接抛出来而不是静默给 0 —— 0
     * 是合法金额，拿它兜底会把「漏查」伪装成「今天没有业务」。
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

    /** 卡片要求的那组聚合必须取过；取不到说明可见卡片与取数编排对不上，属于编码错误。 */
    private static <T> T require(T metrics, ScmDashboardCardEnum card) {
        if (metrics == null) {
            throw new IllegalStateException("卡片 " + card.getKey() + " 可见，但其所属聚合未取数");
        }
        return metrics;
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
     * 入口权限 {@code scm:dashboard:query} 只授权访问工作台本身；不在这里逐个校验，拿到入口权限的人就能顺着 {@code metric} 参数把订单、采购、库存的曲线都拉出来。
     */
    private static void requireQueryPermission(ScmDashboardTrendMetric metric) {
        switch (metric) {
            case SALES -> StpUtil.checkPermission(OrderPermission.QUERY);
            case PURCHASE -> StpUtil.checkPermission(PurchasePermission.QUERY);
            case INVENTORY -> StpUtil.checkPermission(InventoryPermission.MOVEMENT_QUERY);
        }
    }

    /** 排行仅查询所选维度，数据范围与销售指标一致。 */
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
