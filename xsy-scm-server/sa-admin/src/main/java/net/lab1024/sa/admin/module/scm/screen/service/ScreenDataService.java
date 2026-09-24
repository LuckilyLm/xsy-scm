package net.lab1024.sa.admin.module.scm.screen.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryMovementTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryWarningStatusEnum;
import net.lab1024.sa.admin.module.scm.screen.dao.ScreenDataDao;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenBusinessVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenGeoVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenInventoryHealthRow;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenInventoryVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenPurchaseVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenTrendVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据大屏只读聚合服务。
 *
 * <p>只查询，不写业务表；所有统计基于现有业务域，不维护独立副本。
 *
 * <p><b>大屏同样受数据范围约束</b>：它是业务列表的聚合视图，如果这里不收范围，
 * 一个只有 A 仓授权的人拿到 {@code scm:screen:query} 就能读到全公司的成交额、库存量和采购额 ——
 * 聚合值比明细更容易被误当成「已经授权过的数据」。每个入口解析一次上下文再下传给各 Dao，
 * 一次页面加载发十几个 Dao 调用也只解析一次（授权行改动必须立即生效，所以不缓存在登录态里）。
 * 各面板按自己的事实维度收：经营/趋势的销售序列按业务员，采购面板按「采购归属 ∩ 仓库」，
 * 库存与地理的仓库段按仓库；供应商与 SKU 主档没有任何范围维度，按团队共享读处理。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreenDataService {

    private static final int TOP_RANK_LIMIT = 10;

    /**
     * 业务时区。
     *
     * <p><b>「今日」必须是北京时间的今天</b>。早先这里用的是 {@code ZoneOffset.UTC} 的日界，
     * 实际窗口变成「北京时间 08:00 → 次日 08:00」—— 早上 07:00 下的单会被算进前一天，
     * 而 08:00 之后的单才落进当天。这不是显示问题而是口径错误，与需求日期
     * {@code demand_date} 显式使用 {@code AT TIME ZONE 'Asia/Shanghai'} 的约定也不一致。
     */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final String RANGE_7D = "7d";
    private static final String RANGE_30D = "30d";

    /**
     * 入库 / 出库方向的流水类型名，**由枚举的方向位派生**。
     *
     * <p>大屏有三处口径都表达「入库」或「出库」：今日出入库次数、趋势的出入库量、
     * 供应链网络节点的今日出库量。若各自在 SQL 里抄一份类型清单，新增第 11 个流水类型时
     * 会**静默少算**（数字看起来正常，只是偏小）—— 这是本项目最忌讳的失败方式。
     *
     * <p>方向位本身已被 {@code ScmInventoryConstantTest#movementDirectionMatchesSnapshotConstraint}
     * 钉住（10 个类型恰好分成两组、每组 5 个，且与 {@code ck_inventory_movement_snap} 的方向分支同源），
     * 所以从这里派生等于把这三处口径一并接进那道契约守卫。
     */
    private static final List<String> INBOUND_MOVEMENT_TYPES = Arrays
            .stream(ScmInventoryMovementTypeEnum.values())
            .filter(ScmInventoryMovementTypeEnum::isInbound)
            .map(type -> type.name())
            .toList();

    private static final List<String> OUTBOUND_MOVEMENT_TYPES = Arrays
            .stream(ScmInventoryMovementTypeEnum.values())
            .filter(type -> !type.isInbound())
            .map(type -> type.name())
            .toList();

    private final ScreenDataDao screenDataDao;

    private final ScmDataScopeService dataScopeService;

    /**
     * 今日起止（北京时间日界，转成带 +08:00 偏移的瞬间，与库中 TIMESTAMPTZ 可比）。
     */
    private OffsetDateTime[] todayRange() {
        return dayRange(LocalDate.now(BUSINESS_ZONE));
    }

    private OffsetDateTime[] dayRange(LocalDate day) {
        return new OffsetDateTime[]{
                day.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime(),
                day.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime()
        };
    }

    public ScreenBusinessVO getBusinessData() {
        OffsetDateTime[] range = todayRange();
        ScmDataScopeContext scope = dataScopeService.resolve();
        ScreenBusinessVO vo = new ScreenBusinessVO();
        vo.setTodayOrderCount(nullToZero(screenDataDao.countConfirmedOrders(range[0], range[1], scope)));
        vo.setTodayOrderedAmount(nullToZero(screenDataDao.sumOrderedAmount(range[0], range[1], scope)));
        vo.setTodaySettlementAmount(nullToZero(screenDataDao.sumSettlementAmount(range[0], range[1], scope)));
        vo.setTotalOrderCount(nullToZero(screenDataDao.countTotalConfirmedOrders(scope)));
        vo.setTotalSettlementAmount(nullToZero(screenDataDao.sumTotalSettlementAmount(scope)));
        vo.setCustomerCount(nullToZero(screenDataDao.countCustomers(scope)));
        vo.setSupplierCount(nullToZero(screenDataDao.countSuppliers()));
        vo.setSkuCount(nullToZero(screenDataDao.countSkus()));
        vo.setTodayCustomerCount(
                nullToZero(screenDataDao.countCustomersWithOrdersInRange(range[0], range[1], scope)));
        vo.setTodaySupplierCount(
                nullToZero(screenDataDao.countSuppliersWithOrdersInRange(range[0], range[1], scope)));
        vo.setTopCustomers(
                nullToEmpty(screenDataDao.topCustomersBySettlement(range[0], range[1], TOP_RANK_LIMIT, scope)));
        vo.setTopProducts(
                nullToEmpty(screenDataDao.topProductsBySettlement(range[0], range[1], TOP_RANK_LIMIT, scope)));
        return vo;
    }

    public ScreenInventoryVO getInventoryData() {
        OffsetDateTime[] range = todayRange();
        ScmDataScopeContext scope = dataScopeService.resolve();
        ScreenInventoryVO vo = new ScreenInventoryVO();
        vo.setTotalQuantity(nullToZero(screenDataDao.sumInventoryQuantity(scope)));
        vo.setSkuCount(nullToZero(screenDataDao.countInventorySkus(scope)));
        vo.setWarehouseCount(nullToZero(screenDataDao.countEnabledWarehouses(scope)));
        vo.setTodayInboundCount(nullToZero(
                screenDataDao.countMovementsByTypeAndRange(INBOUND_MOVEMENT_TYPES, range[0], range[1], scope)));
        vo.setTodayOutboundCount(nullToZero(
                screenDataDao.countMovementsByTypeAndRange(OUTBOUND_MOVEMENT_TYPES, range[0], range[1], scope)));
        vo.setWarehouseDistribution(nullToEmpty(screenDataDao.inventoryDistributionByWarehouse(scope)));
        vo.setHealth(buildHealth(scope));
        vo.setWarehouseNodes(nullToEmpty(
                screenDataDao.warehouseNetworkNodes(range[0], range[1], OUTBOUND_MOVEMENT_TYPES, scope)));
        return vo;
    }

    /**
     * 库存健康度分档。
     *
     * <p><b>判定完全交给预警枚举</b>（{@link ScmInventoryWarningStatusEnum#evaluate}），
     * 这里只做计数。SQL 只提供「可用量 + 上下限」三个事实，不参与分类 ——
     * 一旦 SQL 里也写一份「正常/低于下限/高于上限」，两份规则漂移后
     * 大屏的健康度就会和预警列表对不上。
     *
     * <p><b>四档互斥且之和等于 {@code totalSkuCount}</b>（设计稿把缺货画成与预警并列的一段，
     * 占比要凑满 100%，所以这里先剔除缺货再判三档，而不是把缺货当子集另计）：
     * <ol>
     *   <li>缺货 —— 可用量 ≤ 0。**优先于其它档**，且不依赖阈值配置，
     *       否则「配了阈值但一件都没有」会被算成预警，而缺货段恒为 0。</li>
     *   <li>未配置阈值 —— 有余额但没配阈值，无法判定，单独成档而不是塞进「正常」。</li>
     *   <li>预警 / 积压 / 正常 —— 由 {@code evaluate} 给出的 LOW / HIGH / NORMAL。</li>
     * </ol>
     */
    private ScreenInventoryVO.InventoryHealth buildHealth(ScmDataScopeContext scope) {
        List<ScreenInventoryHealthRow> rows = nullToEmpty(screenDataDao.inventoryHealthRows(scope));
        long normal = 0L;
        long low = 0L;
        long high = 0L;
        long unconfigured = 0L;
        long outOfStock = 0L;
        for (ScreenInventoryHealthRow row : rows) {
            BigDecimal available = row.getAvailableQuantity();
            // 缺货先判：它比「低于下限」更具体，且不要求配了阈值
            if (available == null || available.signum() <= 0) {
                outOfStock++;
                continue;
            }
            if (row.getWarnMin() == null && row.getWarnMax() == null) {
                unconfigured++;
                continue;
            }
            ScmInventoryWarningStatusEnum status = ScmInventoryWarningStatusEnum.evaluate(
                    available, row.getWarnMin(), row.getWarnMax());
            if (status == ScmInventoryWarningStatusEnum.LOW) {
                low++;
            } else if (status == ScmInventoryWarningStatusEnum.HIGH) {
                high++;
            } else {
                normal++;
            }
        }
        ScreenInventoryVO.InventoryHealth health = new ScreenInventoryVO.InventoryHealth();
        // 总数取参与评估的行数（余额行 ∪ 已配阈值），保证四档之和 = 总数
        health.setTotalSkuCount((long) rows.size());
        health.setNormalCount(normal);
        health.setLowCount(low);
        health.setHighCount(high);
        health.setUnconfiguredCount(unconfigured);
        health.setOutOfStockCount(outOfStock);
        return health;
    }

    public ScreenPurchaseVO getPurchaseData() {
        OffsetDateTime[] range = todayRange();
        ScmDataScopeContext scope = dataScopeService.resolve();
        ScreenPurchaseVO vo = new ScreenPurchaseVO();
        vo.setTodayPurchaseOrderCount(nullToZero(screenDataDao.countPurchaseOrders(range[0], range[1], scope)));
        vo.setTodayPurchaseAmount(nullToZero(screenDataDao.sumPurchaseAmount(range[0], range[1], scope)));
        vo.setTotalPurchaseOrderCount(nullToZero(screenDataDao.countTotalPurchaseOrders(scope)));
        vo.setTotalPurchaseAmount(nullToZero(screenDataDao.sumTotalPurchaseAmount(scope)));
        vo.setTodayReceiptCount(nullToZero(screenDataDao.countReceipts(range[0], range[1], scope)));
        return vo;
    }

    /**
     * 趋势数据（近 7 / 30 天，含今天）。
     *
     * <p>DAO 返回「一天一行」，这里**转置成按序列的数组**：ECharts 的 series 是按序列组织的，
     * 转置放在服务层可以让 SQL 保持「一天一行」这种最好读也最好核对的形状。
     *
     * <p>区间是**闭区间且含今天**：{@code 7d} = 今天往前数 6 天到 今天，共 7 个点。
     */
    public ScreenTrendVO getTrendData(String range) {
        boolean thirty = RANGE_30D.equalsIgnoreCase(range);
        String normalized = thirty ? RANGE_30D : RANGE_7D;
        LocalDate end = LocalDate.now(BUSINESS_ZONE);
        LocalDate start = end.minusDays(thirty ? 29L : 6L);

        List<ScreenTrendVO.Point> points = nullToEmpty(screenDataDao.trendByDay(start, end,
                INBOUND_MOVEMENT_TYPES, OUTBOUND_MOVEMENT_TYPES, dataScopeService.resolve()));

        List<String> dates = new ArrayList<>(points.size());
        List<String> fullDates = new ArrayList<>(points.size());
        List<BigDecimal> sales = new ArrayList<>(points.size());
        List<Long> orders = new ArrayList<>(points.size());
        List<BigDecimal> purchaseAmounts = new ArrayList<>(points.size());
        List<Long> purchaseOrders = new ArrayList<>(points.size());
        List<BigDecimal> inventoryQuantity = new ArrayList<>(points.size());
        List<BigDecimal> inboundQuantity = new ArrayList<>(points.size());
        List<BigDecimal> outboundQuantity = new ArrayList<>(points.size());

        for (ScreenTrendVO.Point p : points) {
            dates.add(p.getLabel());
            fullDates.add(p.getDate());
            sales.add(nullToZero(p.getSales()));
            orders.add(nullToZero(p.getOrders()));
            purchaseAmounts.add(nullToZero(p.getPurchaseAmounts()));
            purchaseOrders.add(nullToZero(p.getPurchaseOrders()));
            inventoryQuantity.add(nullToZero(p.getInventoryQuantity()));
            inboundQuantity.add(nullToZero(p.getInboundQuantity()));
            outboundQuantity.add(nullToZero(p.getOutboundQuantity()));
        }

        ScreenTrendVO vo = new ScreenTrendVO();
        vo.setRange(normalized);
        vo.setDates(dates);
        vo.setFullDates(fullDates);
        vo.setSales(sales);
        vo.setOrders(orders);
        vo.setPurchaseAmounts(purchaseAmounts);
        vo.setPurchaseOrders(purchaseOrders);
        vo.setInventoryQuantity(inventoryQuantity);
        vo.setInboundQuantity(inboundQuantity);
        vo.setOutboundQuantity(outboundQuantity);
        return vo;
    }

    /**
     * 地理分布（地图 M1）。
     *
     * <p>气泡取市级聚合行，省界着色由这批行**在 Java 侧上卷**得到：两者因此恒等，
     * 不存在「省级图例与市级气泡对不上」这种两份 SQL 各自演算的漂移。
     *
     * <p>覆盖度原样透出：{@code 总数 − 已归属} 就是地图上找不到位置的业务量，
     * 必须让用户看到差额，而不是以为看到的分布等于全部业务量。
     */
    public ScreenGeoVO getGeoData() {
        ScmDataScopeContext scope = dataScopeService.resolve();
        List<ScreenGeoVO.CityNode> cities = nullToEmpty(screenDataDao.geoCityRows(scope));
        ScreenGeoVO vo = new ScreenGeoVO();
        vo.setCities(cities);
        vo.setProvinces(rollUpProvinces(cities));
        vo.setCoverage(screenDataDao.geoCoverage(scope));
        return vo;
    }

    /**
     * 省级上卷：只累加市级事实，不引入任何新的判定。
     */
    private static List<ScreenGeoVO.ProvinceNode> rollUpProvinces(List<ScreenGeoVO.CityNode> cities) {
        Map<Integer, ScreenGeoVO.ProvinceNode> byProvince = new LinkedHashMap<>();
        for (ScreenGeoVO.CityNode city : cities) {
            ScreenGeoVO.ProvinceNode province = byProvince.computeIfAbsent(city.getProvinceCode(), code -> {
                ScreenGeoVO.ProvinceNode created = new ScreenGeoVO.ProvinceNode();
                created.setProvinceCode(code);
                created.setProvinceName(city.getProvinceName());
                created.setCityCount(0);
                created.setCustomerCount(0L);
                created.setSupplierCount(0L);
                created.setWarehouseCount(0L);
                return created;
            });
            province.setCityCount(province.getCityCount() + 1);
            province.setCustomerCount(province.getCustomerCount() + city.getCustomerCount());
            province.setSupplierCount(province.getSupplierCount() + city.getSupplierCount());
            province.setWarehouseCount(province.getWarehouseCount() + city.getWarehouseCount());
        }
        List<ScreenGeoVO.ProvinceNode> provinces = new ArrayList<>(byProvince.values());
        provinces.sort(Comparator.comparingLong(ScreenGeoVO.ProvinceNode::getCustomerCount).reversed()
                .thenComparing(ScreenGeoVO.ProvinceNode::getProvinceCode));
        return provinces;
    }

    private static Long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static <T> List<T> nullToEmpty(List<T> value) {
        return value == null ? List.of() : value;
    }
}
