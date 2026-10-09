package com.xsy.scm.metrics.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.inventory.constant.ScmInventoryWarningStatusEnum;
import com.xsy.scm.metrics.constant.ScmMovementDirections;
import com.xsy.scm.metrics.dao.ScmBusinessMetricsDao;
import com.xsy.scm.metrics.domain.InventoryHealth;
import com.xsy.scm.metrics.domain.InventoryHealthRow;
import com.xsy.scm.metrics.domain.InventoryMetrics;
import com.xsy.scm.metrics.domain.MasterDataMetrics;
import com.xsy.scm.metrics.domain.PurchaseFilter;
import com.xsy.scm.metrics.domain.PurchaseMetrics;
import com.xsy.scm.metrics.domain.PurchaseRangeMetrics;
import com.xsy.scm.metrics.domain.RankItem;
import com.xsy.scm.metrics.domain.SalesFilter;
import com.xsy.scm.metrics.domain.SalesMetrics;
import com.xsy.scm.metrics.domain.SalesRangeMetrics;
import com.xsy.scm.metrics.domain.TrendMetrics;
import com.xsy.scm.metrics.domain.TrendPoint;
import com.xsy.scm.metrics.domain.TrendSeriesSelection;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 跨端复用经营指标的统一业务入口：首页 / 大屏 / 报表都从这里取数。
 *
 * <p>
 * 边界分三层，不要把它们混成一件事：<b>业务口径</b>在本类编排（区间怎么取、日界用哪个时区、哪几个字段同轴）； <b>SQL</b> 只在 {@code ScmBusinessMetricsMapper}
 * 定义一处；<b>状态与方向等语义</b>继续复用各业务域的枚举 （如 {@code ScmMovementDirections} 从流水类型的方向位派生），不在 metrics 里再抄一份清单。
 * 「唯一」指的是同一指标只有一处定义，不是「所有统计逻辑都要堆进这个类」。
 *
 * <p>
 * <b>本类只收跨端共用的经营类指标</b>，不是「所有查询的万能入口」：报表专有的按业务员 / 分类分组、对账、账龄、 利润、退款、异常订单、分页导出都留在报表域，进来只会把它变成 God Service。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScmBusinessMetricsService {

    /** 排行默认取前 10：首页取前 5，大屏取前 6，都由调用方截断。 */
    public static final int TOP_RANK_LIMIT = 10;

    /**
     * 业务时区。
     *
     * <p>
     * 「今日」必须是北京时间的今天。用 {@code ZoneOffset.UTC} 的日界会把窗口变成「北京时间 08:00 → 次日 08:00」， 早上 07:00 下的单会被算进前一天 —— 这不是显示问题而是口径错误。
     */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final String RANGE_7D = "7d";
    private static final String RANGE_30D = "30d";

    private final ScmBusinessMetricsDao metricsDao;

    /**
     * 区间内的销售事实（订单数 / 销售额 / 成交客户数），三个字段同轴（{@code confirmed_at}）。
     *
     * <p>
     * 首页 / 大屏直接使用本方法。报表概览因<b>可见性范围策略不同</b>（销售不做归属收窄，由页面权限承担） 保留自己的查询实现，不复用这里 —— 但时间轴、状态与金额列必须与这里保持同一套口径契约， 由
     * {@code scm-metrics-single-source-contract} 钉住。详见 {@code docs/plan/active/home-workbench-design.md} 的「口径同源 ≠
     * 范围同源」一节。
     *
     * <p>
     * {@code filter} 是本方法自带的业务维度筛选（客户 / 业务员 / 订单来源），与范围收窄是两件事。
     */
    public SalesRangeMetrics salesRange(LocalDate startDate, LocalDate endDate, SalesFilter filter,
            ScmDataScopeContext scope) {
        OffsetDateTime[] range = dayRange(startDate, endDate);
        return new SalesRangeMetrics(orZero(metricsDao.countOrdersByConfirmedAt(range[0], range[1], filter, scope)),
                orZero(metricsDao.sumSettlementAmountByConfirmedAt(range[0], range[1], filter, scope)),
                orZero(metricsDao.countCustomersWithOrdersByConfirmedAt(range[0], range[1], filter, scope)));
    }

    /**
     * 今日销售经营指标（首页 / 大屏）。
     *
     * <p>
     * 销售额 / 订单 / 成交客户走确认轴，下单金额走创建轴（见 {@link SalesMetrics}）；区间内的三个数直接复用 {@link #salesRange}，不另写一份 SQL。
     */
    public SalesMetrics todaySales(ScmDataScopeContext scope) {
        OffsetDateTime[] range = todayRange();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        SalesRangeMetrics todayFacts = salesRange(today, today, SalesFilter.NONE, scope);
        return new SalesMetrics(todayFacts.orderCount(),
                orZero(metricsDao.sumOrderedAmountByCreatedAt(range[0], range[1], scope)),
                todayFacts.settlementAmount(), orZero(metricsDao.countTotalOrders(scope)),
                orZero(metricsDao.sumTotalSettlementAmount(scope)), todayFacts.customerCount(),
                nullToEmpty(metricsDao.topCustomersByConfirmedAt(range[0], range[1], TOP_RANK_LIMIT, scope)),
                nullToEmpty(metricsDao.topProductsByConfirmedAt(range[0], range[1], TOP_RANK_LIMIT, scope)));
    }

    public SalesRangeMetrics todaySalesRange(ScmDataScopeContext scope) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        return salesRange(today, today, SalesFilter.NONE, scope);
    }

    public MasterDataMetrics masterData(ScmDataScopeContext scope) {
        return new MasterDataMetrics(orZero(metricsDao.countCustomers(scope)), orZero(metricsDao.countSuppliers()),
                orZero(metricsDao.countSkus()));
    }

    public List<RankItem> todayTopCustomers(int limit, ScmDataScopeContext scope) {
        OffsetDateTime[] range = todayRange();
        return nullToEmpty(metricsDao.topCustomersByConfirmedAt(range[0], range[1], boundedRankLimit(limit), scope));
    }

    public List<RankItem> todayTopProducts(int limit, ScmDataScopeContext scope) {
        OffsetDateTime[] range = todayRange();
        return nullToEmpty(metricsDao.topProductsByConfirmedAt(range[0], range[1], boundedRankLimit(limit), scope));
    }

    private static int boundedRankLimit(int limit) {
        return Math.max(1, Math.min(limit, TOP_RANK_LIMIT));
    }

    /**
     * 区间内的已提交采购事实（采购单数 / 金额），两个字段同轴（{@code submitted_at}）且只算已提交状态。
     *
     * <p>
     * 首页 / 大屏直接使用本方法。报表的采购分析因<b>可见性范围策略不同</b>（只按仓库收窄，不按采购归属收窄） 保留自己的查询实现，不复用这里 —— 但时间轴、状态与金额列必须与这里保持同一套口径契约，
     * 其中「已提交状态清单」由 {@code ScmPurchaseStatusEnum.committedNames()} 传参给两边的 SQL，只有一处来源。
     */
    public PurchaseRangeMetrics purchaseRange(LocalDate startDate, LocalDate endDate, PurchaseFilter filter,
            ScmDataScopeContext scope) {
        OffsetDateTime[] range = dayRange(startDate, endDate);
        List<String> committed = ScmPurchaseStatusEnum.committedNames();
        return new PurchaseRangeMetrics(
                orZero(metricsDao.countPurchaseOrdersBySubmittedAt(committed, range[0], range[1], filter, scope)),
                orZero(metricsDao.sumPurchaseAmountBySubmittedAt(committed, range[0], range[1], filter, scope)));
    }

    /**
     * 今日采购经营指标（首页 / 大屏）。
     *
     * <p>
     * 区间内的两个数直接复用 {@link #purchaseRange}，不另写一份 SQL；累计口径与今日同源（也是已提交）。
     */
    public PurchaseMetrics todayPurchase(ScmDataScopeContext scope) {
        OffsetDateTime[] range = todayRange();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        List<String> committed = ScmPurchaseStatusEnum.committedNames();
        PurchaseRangeMetrics todayFacts = purchaseRange(today, today, PurchaseFilter.NONE, scope);
        return new PurchaseMetrics(todayFacts.orderCount(), todayFacts.amount(),
                orZero(metricsDao.countTotalPurchaseOrders(committed, scope)),
                orZero(metricsDao.sumTotalPurchaseAmount(committed, scope)),
                orZero(metricsDao.countReceipts(range[0], range[1], scope)), activeSupplierCount(scope, range));
    }

    /**
     * 今日活跃供应商数（有已提交采购单的供应商去重）。
     *
     * <p>
     * 单独暴露给只需要这一个数字的调用方（大屏经营面板就是）：它不必为了一个值去跑整组采购指标。 与 {@link #todayPurchase} 内部走的是同一条 SQL，不存在第二个口径。
     */
    public long activeSupplierCount(ScmDataScopeContext scope) {
        return activeSupplierCount(scope, todayRange());
    }

    private long activeSupplierCount(ScmDataScopeContext scope, OffsetDateTime[] range) {
        return orZero(metricsDao.countSuppliersWithOrdersBySubmittedAt(ScmPurchaseStatusEnum.committedNames(), range[0],
                range[1], scope));
    }

    public InventoryMetrics inventory(ScmDataScopeContext scope) {
        OffsetDateTime[] range = todayRange();
        return new InventoryMetrics(orZero(metricsDao.sumInventoryQuantity(scope)),
                orZero(metricsDao.countInventorySkus(scope)), orZero(metricsDao.countEnabledWarehouses(scope)),
                orZero(metricsDao.countMovementsByTypeAndRange(ScmMovementDirections.inboundTypes(), range[0], range[1],
                        scope)),
                orZero(metricsDao.countMovementsByTypeAndRange(ScmMovementDirections.outboundTypes(), range[0],
                        range[1], scope)),
                inventoryHealth(scope));
    }

    /**
     * 趋势：近 7 / 30 天（闭区间含今天），转置成按序列的数组。
     *
     * <p>
     * 转置放在服务层，SQL 保持「一天一行」这种最好读也最好核对的形状。{@code range} 取非 30d 一律按 7d 处理， 不抛错 —— URL 上的取值不该让整个面板失败。
     */
    public TrendMetrics trend(String range, ScmDataScopeContext scope) {
        return trend(range, scope, TrendSeriesSelection.ALL);
    }

    public TrendMetrics trend(String range, ScmDataScopeContext scope, TrendSeriesSelection selection) {
        Objects.requireNonNull(selection, "趋势序列选择不能为空");
        boolean thirty = RANGE_30D.equalsIgnoreCase(range);
        LocalDate end = LocalDate.now(BUSINESS_ZONE);
        LocalDate start = end.minusDays(thirty ? 29L : 6L);

        List<TrendPoint> points = nullToEmpty(TrendSeriesSelection.ALL.equals(selection)
                ? metricsDao.trendByDay(start, end, ScmMovementDirections.inboundTypes(),
                        ScmMovementDirections.outboundTypes(), ScmPurchaseStatusEnum.committedNames(), scope)
                : metricsDao.selectedTrendByDay(start, end, ScmMovementDirections.inboundTypes(),
                        ScmMovementDirections.outboundTypes(), ScmPurchaseStatusEnum.committedNames(), scope,
                        selection));

        List<String> dates = new ArrayList<>(points.size());
        List<String> fullDates = new ArrayList<>(points.size());
        List<BigDecimal> sales = new ArrayList<>(points.size());
        List<Long> orders = new ArrayList<>(points.size());
        List<BigDecimal> purchaseAmounts = new ArrayList<>(points.size());
        List<Long> purchaseOrders = new ArrayList<>(points.size());
        List<BigDecimal> inventoryQuantity = new ArrayList<>(points.size());
        List<BigDecimal> inboundQuantity = new ArrayList<>(points.size());
        List<BigDecimal> outboundQuantity = new ArrayList<>(points.size());

        for (TrendPoint point : points) {
            dates.add(point.label());
            fullDates.add(point.date());
            sales.add(orZero(point.sales()));
            orders.add(orZero(point.orders()));
            purchaseAmounts.add(orZero(point.purchaseAmounts()));
            purchaseOrders.add(orZero(point.purchaseOrders()));
            inventoryQuantity.add(orZero(point.inventoryQuantity()));
            inboundQuantity.add(orZero(point.inboundQuantity()));
            outboundQuantity.add(orZero(point.outboundQuantity()));
        }

        return new TrendMetrics(thirty ? RANGE_30D : RANGE_7D, dates, fullDates, sales, orders, purchaseAmounts,
                purchaseOrders, inventoryQuantity, inboundQuantity, outboundQuantity);
    }

    /**
     * 库存健康度分档。
     *
     * <p>
     * 判定完全交给预警枚举，这里只做计数；SQL 只提供「可用量 + 上下限」三个事实，不参与分类 —— 一旦 SQL 里也写一份 「正常 / 低于下限 / 高于上限」，两份规则漂移后健康度就会和预警列表对不上。
     *
     * <p>
     * 缺货优先于其它档，且不依赖阈值配置，否则「配了阈值但一件都没有」会被算成预警，而缺货段恒为 0。
     */
    private InventoryHealth inventoryHealth(ScmDataScopeContext scope) {
        List<InventoryHealthRow> rows = nullToEmpty(metricsDao.inventoryHealthRows(scope));
        long normal = 0L;
        long low = 0L;
        long high = 0L;
        long unconfigured = 0L;
        long outOfStock = 0L;
        for (InventoryHealthRow row : rows) {
            BigDecimal available = row.availableQuantity();
            if (available == null || available.signum() <= 0) {
                outOfStock++;
                continue;
            }
            if (row.warnMin() == null && row.warnMax() == null) {
                unconfigured++;
                continue;
            }
            ScmInventoryWarningStatusEnum status = ScmInventoryWarningStatusEnum.evaluate(available, row.warnMin(),
                    row.warnMax());
            if (status == ScmInventoryWarningStatusEnum.LOW) {
                low++;
            } else if (status == ScmInventoryWarningStatusEnum.HIGH) {
                high++;
            } else {
                normal++;
            }
        }
        // 总数取参与评估的行数（余额行 ∪ 已配阈值），保证各档之和 = 总数
        return new InventoryHealth(rows.size(), normal, low, high, unconfigured, outOfStock);
    }

    private OffsetDateTime[] todayRange() {
        return dayRange(LocalDate.now(BUSINESS_ZONE));
    }

    private static OffsetDateTime[] dayRange(LocalDate day) {
        return dayRange(day, day);
    }

    /** 闭区间日期 → 半开瞬间区间，与报表的日界换算同源（起始日 00:00 含，结束日次日 00:00 不含）。 */
    private static OffsetDateTime[] dayRange(LocalDate startDate, LocalDate endDate) {
        return new OffsetDateTime[]{startDate.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime(),
                endDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime()};
    }

    private static long orZero(Long value) {
        return Objects.requireNonNullElse(value, 0L);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO);
    }

    private static <T> List<T> nullToEmpty(List<T> value) {
        return value == null ? List.of() : value;
    }
}
