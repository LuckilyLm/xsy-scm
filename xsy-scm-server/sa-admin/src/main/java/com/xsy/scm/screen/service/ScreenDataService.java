package com.xsy.scm.screen.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.metrics.constant.ScmMovementDirections;
import com.xsy.scm.metrics.domain.InventoryHealth;
import com.xsy.scm.metrics.domain.InventoryMetrics;
import com.xsy.scm.metrics.domain.MasterDataMetrics;
import com.xsy.scm.metrics.domain.PurchaseMetrics;
import com.xsy.scm.metrics.domain.RankItem;
import com.xsy.scm.metrics.domain.SalesMetrics;
import com.xsy.scm.metrics.domain.TrendMetrics;
import com.xsy.scm.metrics.service.ScmBusinessMetricsService;
import com.xsy.scm.screen.dao.ScreenDataDao;
import com.xsy.scm.screen.domain.vo.ScreenBusinessVO;
import com.xsy.scm.screen.domain.vo.ScreenGeoVO;
import com.xsy.scm.screen.domain.vo.ScreenInventoryVO;
import com.xsy.scm.screen.domain.vo.ScreenPurchaseVO;
import com.xsy.scm.screen.domain.vo.ScreenTrendVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据大屏只读聚合服务：只做「取指标 + 装成大屏面板」两件事。
 *
 * <p>
 * <b>经营类指标不在这里算</b>，一律向 {@link ScmBusinessMetricsService} 取 —— 大屏、首页、报表对同一件事必须给出同一个数。
 * 本类只保留呈现形态属于大屏自己的查询（仓库分布条、供应链网络节点、地理分布）。
 *
 * <p>
 * <b>大屏同样受数据范围约束</b>：它是业务列表的聚合视图，如果这里不收范围，一个只有 A 仓授权的人拿到 {@code scm:screen:query}
 * 就能读到全公司的成交额、库存量和采购额。每个入口解析一次上下文再下传，一次页面加载发十几个 Dao 调用也只解析一次 （授权行改动必须立即生效，所以不缓存在登录态里）。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreenDataService {

    /**
     * 业务时区。
     *
     * <p>
     * 大屏自己的两条查询（网络节点的今日出库量）仍要算「今日」。用 UTC 日界会把窗口整体平移 8 小时，与其余面板的口径不一致。
     */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ScreenDataDao screenDataDao;

    private final ScmBusinessMetricsService metricsService;

    private final ScmDataScopeService dataScopeService;

    public ScreenBusinessVO getBusinessData() {
        ScmDataScopeContext scope = dataScopeService.resolve();
        SalesMetrics sales = metricsService.todaySales(scope);
        MasterDataMetrics masterData = metricsService.masterData(scope);

        ScreenBusinessVO vo = new ScreenBusinessVO();
        vo.setTodayOrderCount(sales.todayOrderCount());
        vo.setTodayOrderedAmount(sales.todayOrderedAmount());
        vo.setTodaySettlementAmount(sales.todaySettlementAmount());
        vo.setTotalOrderCount(sales.totalOrderCount());
        vo.setTotalSettlementAmount(sales.totalSettlementAmount());
        vo.setCustomerCount(masterData.customerCount());
        vo.setSupplierCount(masterData.supplierCount());
        vo.setSkuCount(masterData.skuCount());
        vo.setTodayCustomerCount(sales.todayCustomerCount());
        vo.setTodaySupplierCount(metricsService.activeSupplierCount(scope));
        vo.setTopCustomers(toRankItems(sales.topCustomers()));
        vo.setTopProducts(toRankItems(sales.topProducts()));
        return vo;
    }

    public ScreenInventoryVO getInventoryData() {
        ScmDataScopeContext scope = dataScopeService.resolve();
        InventoryMetrics metrics = metricsService.inventory(scope);

        ScreenInventoryVO vo = new ScreenInventoryVO();
        vo.setTotalQuantity(metrics.totalQuantity());
        vo.setSkuCount(metrics.skuCount());
        vo.setWarehouseCount(metrics.warehouseCount());
        vo.setTodayInboundCount(metrics.todayInboundCount());
        vo.setTodayOutboundCount(metrics.todayOutboundCount());
        vo.setHealth(toScreenHealth(metrics.health()));
        vo.setWarehouseDistribution(nullToEmpty(screenDataDao.inventoryDistributionByWarehouse(scope)));
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        vo.setWarehouseNodes(
                nullToEmpty(screenDataDao.warehouseNetworkNodes(today.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime(),
                        today.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime(),
                        ScmMovementDirections.outboundTypes(), scope)));
        return vo;
    }

    public ScreenPurchaseVO getPurchaseData() {
        PurchaseMetrics metrics = metricsService.todayPurchase(dataScopeService.resolve());
        ScreenPurchaseVO vo = new ScreenPurchaseVO();
        vo.setTodayPurchaseOrderCount(metrics.todayPurchaseOrderCount());
        vo.setTodayPurchaseAmount(metrics.todayPurchaseAmount());
        vo.setTotalPurchaseOrderCount(metrics.totalPurchaseOrderCount());
        vo.setTotalPurchaseAmount(metrics.totalPurchaseAmount());
        vo.setTodayReceiptCount(metrics.todayReceiptCount());
        return vo;
    }

    /**
     * 趋势数据（近 7 / 30 天，含今天）。
     *
     * <p>
     * 区间是<b>闭区间且含今天</b>：{@code 7d} = 今天往前数 6 天到今天，共 7 个点。序列口径由 metrics 服务负责， 这里只把结果搬进大屏 VO。
     */
    public ScreenTrendVO getTrendData(String range) {
        TrendMetrics metrics = metricsService.trend(range, dataScopeService.resolve());
        ScreenTrendVO vo = new ScreenTrendVO();
        vo.setRange(metrics.range());
        vo.setDates(metrics.dates());
        vo.setFullDates(metrics.fullDates());
        vo.setSales(metrics.sales());
        vo.setOrders(metrics.orders());
        vo.setPurchaseAmounts(metrics.purchaseAmounts());
        vo.setPurchaseOrders(metrics.purchaseOrders());
        vo.setInventoryQuantity(metrics.inventoryQuantity());
        vo.setInboundQuantity(metrics.inboundQuantity());
        vo.setOutboundQuantity(metrics.outboundQuantity());
        return vo;
    }

    /**
     * 地理分布（地图 M1）。
     *
     * <p>
     * 气泡取市级聚合行，省界着色由这批行<b>在 Java 侧上卷</b>得到：两者因此恒等，不存在「省级图例与市级气泡对不上」这种两份 SQL 各自演算的漂移。
     *
     * <p>
     * 覆盖度原样透出：{@code 总数 − 已归属} 就是地图上找不到位置的业务量，必须让用户看到差额，而不是以为看到的分布等于全部业务量。
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

    private static List<ScreenBusinessVO.RankItem> toRankItems(List<RankItem> source) {
        List<ScreenBusinessVO.RankItem> items = new ArrayList<>(source.size());
        for (RankItem item : source) {
            ScreenBusinessVO.RankItem target = new ScreenBusinessVO.RankItem();
            target.setName(item.name());
            target.setAmount(item.amount());
            items.add(target);
        }
        return items;
    }

    private static ScreenInventoryVO.InventoryHealth toScreenHealth(InventoryHealth health) {
        ScreenInventoryVO.InventoryHealth target = new ScreenInventoryVO.InventoryHealth();
        target.setTotalSkuCount(health.totalSkuCount());
        target.setNormalCount(health.normalCount());
        target.setLowCount(health.lowCount());
        target.setHighCount(health.highCount());
        target.setUnconfiguredCount(health.unconfiguredCount());
        target.setOutOfStockCount(health.outOfStockCount());
        return target;
    }

    private static <T> List<T> nullToEmpty(List<T> value) {
        return value == null ? List.of() : value;
    }
}
