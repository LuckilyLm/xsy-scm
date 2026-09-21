package net.lab1024.sa.admin.module.scm.screen;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryWarningQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryWarningVO;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryWarningQueryService;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenBusinessVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenGeoVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenInventoryVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenPurchaseVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenTrendVO;
import net.lab1024.sa.admin.module.scm.screen.service.ScreenDataService;
import net.lab1024.sa.base.common.domain.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B7 数据大屏聚合服务集成测试。
 *
 * <p>验证只读聚合接口从现有业务表（sales_order / purchase_order / inventory_balance 等）
 * 统计出的指标正确。
 *
 * <p><b>为什么不断言「空库返回零」</b>：本类的基类把用例包在一个事务里，但同一套件里
 * {@code Propagation.NOT_SUPPORTED} 的 IT（库存回滚 / 并发 / 调拨回滚）会把数据**提交**进库，
 * 因此「累计类指标为 0」只在特定执行顺序下成立 —— 那是一条依赖测试顺序的脆弱断言
 * （曾稳定失败：期望 0、实际 17）。现在断言的是**真实成立的口径**：
 * 字段非 null（契约是「返回零值而不是 null」）、非负、且今日量不超过累计量。
 */
@DisplayName("B7 数据大屏聚合服务（PG IT）")
class ScmScreenDataIT extends ScmW6PgITBase {

    @Autowired
    private ScreenDataService screenDataService;

    @Autowired
    private InventoryWarningQueryService inventoryWarningQueryService;

    @Test
    @DisplayName("经营数据：字段非 null、非负且口径自洽")
    void businessDataFieldsAreNonNullAndConsistent() {
        ScreenBusinessVO vo = screenDataService.getBusinessData();

        assertThat(vo.getTodayOrderCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getTodayOrderedAmount()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(vo.getTodaySettlementAmount()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(vo.getTotalOrderCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getTotalSettlementAmount()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(vo.getCustomerCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getSupplierCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getSkuCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getTodayCustomerCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getTodaySupplierCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        // 排行榜本身必须非 null —— 前端直接 v-for，null 会崩
        assertThat(vo.getTopCustomers()).isNotNull();
        assertThat(vo.getTopProducts()).isNotNull();

        // 口径自洽：今日量是累计量的子集，不可能超过
        assertThat(vo.getTodayOrderCount()).isLessThanOrEqualTo(vo.getTotalOrderCount());
        assertThat(vo.getTodaySettlementAmount()).isLessThanOrEqualTo(vo.getTotalSettlementAmount());
        // 「今日成交客户」是客户总表的子集
        assertThat(vo.getTodayCustomerCount()).isLessThanOrEqualTo(vo.getCustomerCount());
        assertThat(vo.getTodaySupplierCount()).isLessThanOrEqualTo(vo.getSupplierCount());
    }

    @Test
    @DisplayName("库存数据：字段非 null、非负且口径自洽")
    void inventoryDataFieldsAreNonNullAndConsistent() {
        ScreenInventoryVO vo = screenDataService.getInventoryData();

        assertThat(vo.getTotalQuantity()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(vo.getSkuCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getWarehouseCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getTodayInboundCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getTodayOutboundCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getWarehouseDistribution()).isNotNull();

        // 仓库分布的每一行都必须有非空仓名 —— 这是「按仓库聚合」联表取名的回归点：
        // 曾经写成 w.warehouse_name（warehouse 表的列实际是 name），SQL 直接报列不存在。
        vo.getWarehouseDistribution().forEach(row -> {
            assertThat(row.getWarehouseName()).isNotBlank();
            assertThat(row.getQuantity()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        });
    }

    @Test
    @DisplayName("采购数据：字段非 null、非负且口径自洽")
    void purchaseDataFieldsAreNonNullAndConsistent() {
        ScreenPurchaseVO vo = screenDataService.getPurchaseData();

        assertThat(vo.getTodayPurchaseOrderCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getTodayPurchaseAmount()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(vo.getTotalPurchaseOrderCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getTotalPurchaseAmount()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(vo.getTodayReceiptCount()).isNotNull().isGreaterThanOrEqualTo(0L);

        assertThat(vo.getTodayPurchaseOrderCount()).isLessThanOrEqualTo(vo.getTotalPurchaseOrderCount());
        assertThat(vo.getTodayPurchaseAmount()).isLessThanOrEqualTo(vo.getTotalPurchaseAmount());
    }

    @Test
    @DisplayName("库存健康度：四档互斥且之和等于总数，并与预警列表交叉验证")
    void inventoryHealthMatchesWarningList() {
        ScreenInventoryVO vo = screenDataService.getInventoryData();
        ScreenInventoryVO.InventoryHealth health = vo.getHealth();
        assertThat(health).isNotNull();
        assertThat(health.getTotalSkuCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(health.getNormalCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(health.getLowCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(health.getHighCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(health.getUnconfiguredCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(health.getOutOfStockCount()).isNotNull().isGreaterThanOrEqualTo(0L);

        // 四档互斥且**之和恰好等于总数**（设计稿把缺货画成与预警并列的一段，占比要凑满 100%）。
        // 这是分档实现的核心不变量：一旦有人把缺货改回「与三档重叠的子集」，
        // 占比之和会超过 100%，而界面上看不出任何异常。
        long sum = health.getNormalCount() + health.getLowCount() + health.getHighCount()
                + health.getOutOfStockCount() + health.getUnconfiguredCount();
        assertThat(sum)
                .as("正常+预警+积压+缺货+未配置 应等于参与评估的总数")
                .isEqualTo(health.getTotalSkuCount());

        // 交叉验证：大屏健康度与预警列表是同一条业务规则的两条实现路径
        // （Java 枚举 vs 列表 SQL 谓词）—— 一旦漂移，两边会对不上。
        //
        // 注意两边**口径并不完全重合**，所以只能断言夹逼而不是相等：
        //   预警列表 FROM inventory_warning_threshold，只包含「配了阈值」的 (仓库,SKU)；
        //   健康度还包含「没配阈值但可用量 ≤ 0」的余额行（这些行列表里根本没有）。
        // 于是：
        //   下界 low+high —— 每个低于下限/高于上限的档都配了阈值且 evaluate ≠ NORMAL，必然在列表里；
        //   上界 low+high+outOfStock —— 列表里的行要么可用量 > 0（落在 low/high），
        //                              要么可用量 ≤ 0（落在缺货），没有第三种。
        InventoryWarningQueryForm form = new InventoryWarningQueryForm();
        form.setPageNum(1L);
        form.setPageSize(100L);
        PageResult<InventoryWarningVO> page = inventoryWarningQueryService.queryWarningPage(form);
        long abnormal = health.getLowCount() + health.getHighCount();
        assertThat(page.getTotal())
                .as("预警列表异常总数应落在 [低于下限+高于上限, 该值+缺货] 之间")
                .isGreaterThanOrEqualTo(abnormal)
                .isLessThanOrEqualTo(abnormal + health.getOutOfStockCount());

        // 供应链网络节点：只含启用仓库，且字段完整（前端直接渲染，null 会崩）
        assertThat(vo.getWarehouseNodes()).isNotNull();
        vo.getWarehouseNodes().forEach(node -> {
            assertThat(node.getWarehouseName()).isNotBlank();
            assertThat(node.getQuantity()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(node.getTodayOutboundQuantity()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        });
        assertThat(vo.getWarehouseNodes()).hasSize(vo.getWarehouseCount().intValue());
    }

    @Test
    @DisplayName("地理分布：省级上卷与市级气泡恒等，覆盖度自洽且与其它面板同口径")
    void geoDataRollupAndCoverageAreConsistent() {
        ScreenGeoVO geo = screenDataService.getGeoData();
        // 契约是「空数组而不是 null」：前端直接把它喂给 ECharts 的 series.data
        assertThat(geo.getCities()).isNotNull();
        assertThat(geo.getProvinces()).isNotNull();
        assertThat(geo.getCoverage()).isNotNull();

        long cityCustomers = 0L;
        long citySuppliers = 0L;
        long cityWarehouses = 0L;
        for (ScreenGeoVO.CityNode city : geo.getCities()) {
            assertThat(city.getCityCode()).isNotNull();
            assertThat(city.getCityName()).isNotBlank();
            assertThat(city.getProvinceCode()).isNotNull();
            assertThat(city.getProvinceName()).isNotBlank();
            // 坐标是区划质心（GCJ-02）。落在中国范围外 = 存错坐标系或列串位，
            // 两种都会把气泡画到图外，而数字看起来完全正常。
            assertThat(city.getCenterLng()).isBetween(BigDecimal.valueOf(73), BigDecimal.valueOf(136));
            assertThat(city.getCenterLat()).isBetween(BigDecimal.valueOf(3), BigDecimal.valueOf(54));
            assertThat(city.getCustomerCount()).isNotNull().isGreaterThanOrEqualTo(0L);
            assertThat(city.getSupplierCount()).isNotNull().isGreaterThanOrEqualTo(0L);
            assertThat(city.getWarehouseCount()).isNotNull().isGreaterThanOrEqualTo(0L);
            cityCustomers += city.getCustomerCount();
            citySuppliers += city.getSupplierCount();
            cityWarehouses += city.getWarehouseCount();
        }

        // 省级是市级**上卷**而不是第二次聚合，因此三档量与市数都必须守恒
        assertThat(geo.getProvinces()).extracting(ScreenGeoVO.ProvinceNode::getProvinceCode).doesNotHaveDuplicates();
        long provinceCustomers = 0L;
        long provinceSuppliers = 0L;
        long provinceWarehouses = 0L;
        long provinceCityCount = 0L;
        for (ScreenGeoVO.ProvinceNode province : geo.getProvinces()) {
            provinceCustomers += province.getCustomerCount();
            provinceSuppliers += province.getSupplierCount();
            provinceWarehouses += province.getWarehouseCount();
            provinceCityCount += province.getCityCount();
        }
        assertThat(provinceCustomers).isEqualTo(cityCustomers);
        assertThat(provinceSuppliers).isEqualTo(citySuppliers);
        assertThat(provinceWarehouses).isEqualTo(cityWarehouses);
        assertThat(provinceCityCount).isEqualTo(geo.getCities().size());

        ScreenGeoVO.Coverage coverage = geo.getCoverage();
        assertThat(coverage.getCustomerLocated()).isLessThanOrEqualTo(coverage.getCustomerTotal());
        assertThat(coverage.getSupplierLocated()).isLessThanOrEqualTo(coverage.getSupplierTotal());
        assertThat(coverage.getWarehouseLocated()).isLessThanOrEqualTo(coverage.getWarehouseTotal());
        // 气泡之和 ≤ 已归属数：字典里查不到的编码会被 JOIN 丢掉，但绝不可能凭空多出来
        assertThat(cityCustomers).isLessThanOrEqualTo(coverage.getCustomerLocated());
        assertThat(citySuppliers).isLessThanOrEqualTo(coverage.getSupplierLocated());
        assertThat(cityWarehouses).isLessThanOrEqualTo(coverage.getWarehouseLocated());
        // 跨面板口径：地理分布的分母与经营/库存面板的总数必须是同一件事，仓库同样只算启用仓
        assertThat(coverage.getCustomerTotal()).isEqualTo(screenDataService.getBusinessData().getCustomerCount());
        assertThat(coverage.getSupplierTotal()).isEqualTo(screenDataService.getBusinessData().getSupplierCount());
        assertThat(coverage.getWarehouseTotal()).isEqualTo(screenDataService.getInventoryData().getWarehouseCount());
    }

    /**
     * 精确计数：上一用例只能夹逼（库里有别人提交的数据、归属状况未知），这里把全部主档
     * 显式搬到已知的两个市上，逐字段验证聚合结果 == jdbc 独立算出的期望值。
     *
     * <p>分桶故意做成「一个市只有客户」：广州没有供应商与仓库，才能同时抓到
     * 「按 kind 分列计数串位」和「空档被写成 null 而不是 0」两类缺陷。
     */
    @Test
    @DisplayName("地理分布：全部主档归属到市时气泡与省级上卷计数精确相等")
    void geoCountsAreExactWhenEveryMasterIsLocated() {
        newCustomer();
        Long customerInGuangzhou = newCustomer();
        newSupplier("GEO-S");
        newWarehouse("GEO-W");

        jdbc.update("UPDATE customer SET province_code = 330000, city_code = 330100 WHERE deleted = FALSE");
        jdbc.update("UPDATE customer SET province_code = 440000, city_code = 440100 WHERE id = ?",
                customerInGuangzhou);
        jdbc.update("UPDATE supplier SET province_code = 330000, city_code = 330100 WHERE deleted = FALSE");
        jdbc.update("UPDATE warehouse SET province_code = 330000, city_code = 330100 WHERE deleted = FALSE");
        // 前置守卫：杭州桶为空会让下面所有 `- 1` 断言在别处失败，看不出真正的原因
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer WHERE deleted = FALSE "
                + "AND id <> ?", Integer.class, customerInGuangzhou)).isGreaterThan(0);

        long customerTotal = jdbc.queryForObject("SELECT count(*) FROM customer WHERE deleted = FALSE", Long.class);
        long supplierTotal = jdbc.queryForObject("SELECT count(*) FROM supplier WHERE deleted = FALSE", Long.class);
        long warehouseTotal = jdbc.queryForObject("SELECT count(*) FROM warehouse "
                + "WHERE deleted = FALSE AND status = 'ENABLED'", Long.class);

        evictMybatisCache();
        ScreenGeoVO geo = screenDataService.getGeoData();

        // 气泡：只有客户所在的两个市，且数量精确
        assertThat(geo.getCities()).extracting(ScreenGeoVO.CityNode::getCityCode)
                .containsExactlyInAnyOrder(330100, 440100);
        ScreenGeoVO.CityNode hangzhou = cityOf(geo, 330100);
        ScreenGeoVO.CityNode guangzhou = cityOf(geo, 440100);
        assertThat(hangzhou.getCustomerCount()).isEqualTo(customerTotal - 1);
        assertThat(hangzhou.getSupplierCount()).isEqualTo(supplierTotal);
        assertThat(hangzhou.getWarehouseCount()).isEqualTo(warehouseTotal);
        assertThat(guangzhou.getCustomerCount()).isEqualTo(1L);
        // 无供应商 / 无仓库的档位必须是 0：契约里没有 null，前端直接拿它做气泡尺寸
        assertThat(guangzhou.getSupplierCount()).isZero();
        assertThat(guangzhou.getWarehouseCount()).isZero();
        assertThat(hangzhou.getCityName()).isEqualTo("杭州市");
        assertThat(guangzhou.getProvinceName()).isEqualTo("广东省");

        // 省级上卷：省集与市集一致，数值来自同一批市级事实
        assertThat(geo.getProvinces()).extracting(ScreenGeoVO.ProvinceNode::getProvinceCode)
                .containsExactlyInAnyOrder(330000, 440000);
        ScreenGeoVO.ProvinceNode zhejiang = provinceOf(geo, 330000);
        assertThat(zhejiang.getCityCount()).isEqualTo(1);
        assertThat(zhejiang.getCustomerCount()).isEqualTo(customerTotal - 1);
        assertThat(zhejiang.getSupplierCount()).isEqualTo(supplierTotal);
        assertThat(zhejiang.getWarehouseCount()).isEqualTo(warehouseTotal);
        ScreenGeoVO.ProvinceNode guangdong = provinceOf(geo, 440000);
        assertThat(guangdong.getCityCount()).isEqualTo(1);
        assertThat(guangdong.getCustomerCount()).isEqualTo(1L);
        assertThat(guangdong.getSupplierCount()).isZero();
        assertThat(guangdong.getWarehouseCount()).isZero();

        ScreenGeoVO.Coverage coverage = geo.getCoverage();
        assertThat(coverage.getCustomerLocated()).isEqualTo(customerTotal);
        assertThat(coverage.getSupplierLocated()).isEqualTo(supplierTotal);
        assertThat(coverage.getWarehouseLocated()).isEqualTo(warehouseTotal);

        // 字典外编码：`located` 只问「有没有市码」，气泡却要 JOIN 字典才画得出来。
        // 于是这条客户被算进「已归属」，同时从图上消失 —— 差额只能在覆盖度里被看见，
        // 这正是 coverage 存在的理由，所以把它钉成断言而不是留在注释里。
        jdbc.update("UPDATE customer SET city_code = 999999 WHERE id = ?", customerInGuangzhou);
        evictMybatisCache();
        ScreenGeoVO afterBogusCode = screenDataService.getGeoData();
        assertThat(afterBogusCode.getCities()).extracting(ScreenGeoVO.CityNode::getCityCode)
                .containsExactly(330100);
        assertThat(cityOf(afterBogusCode, 330100).getCustomerCount()).isEqualTo(customerTotal - 1);
        assertThat(afterBogusCode.getCoverage().getCustomerLocated()).isEqualTo(customerTotal);
    }

    /**
     * 按市码取节点；取不到就是断言失败，而不是让调用方去和 null 较劲。
     */
    private static ScreenGeoVO.CityNode cityOf(ScreenGeoVO geo, int cityCode) {
        for (ScreenGeoVO.CityNode city : geo.getCities()) {
            if (city.getCityCode() == cityCode) {
                return city;
            }
        }
        throw new AssertionError("市级节点缺失: " + cityCode);
    }

    private static ScreenGeoVO.ProvinceNode provinceOf(ScreenGeoVO geo, int provinceCode) {
        for (ScreenGeoVO.ProvinceNode province : geo.getProvinces()) {
            if (province.getProvinceCode() == provinceCode) {
                return province;
            }
        }
        throw new AssertionError("省级节点缺失: " + provinceCode);
    }

    @Test
    @DisplayName("趋势数据：7d/30d 序列长度一致、日期轴对齐且末点就是今天")
    void trendDataSeriesAreAlignedAndConsistent() {
        assertSeriesAligned(screenDataService.getTrendData("7d"), 7);
        assertSeriesAligned(screenDataService.getTrendData("30d"), 30);

        // 未知/缺失取值一律按 7d 兜底：大屏不应因为一个参数笔误而整块空白
        assertThat(screenDataService.getTrendData("bogus").getRange()).isEqualTo("7d");
        assertThat(screenDataService.getTrendData("bogus").getDates()).hasSize(7);
        assertThat(screenDataService.getTrendData(null).getRange()).isEqualTo("7d");
    }

    /**
     * 断言八条序列与日期轴长度一致，且末点就是**北京时间的今天**。
     *
     * <p>末点日期这条断言是「今日时区」的回归点：服务层曾经用 UTC 日界，
     * 窗口整体平移 8 小时，早上下的单会被算到前一天，而序列长度看起来完全正常 ——
     * 只看长度是抓不到的。
     */
    private void assertSeriesAligned(ScreenTrendVO vo, int expected) {
        assertThat(vo.getRange()).isEqualTo(expected == 7 ? "7d" : "30d");
        assertThat(vo.getDates()).hasSize(expected);
        assertThat(vo.getFullDates()).hasSize(expected);
        assertThat(vo.getSales()).hasSize(expected);
        assertThat(vo.getOrders()).hasSize(expected);
        assertThat(vo.getPurchaseAmounts()).hasSize(expected);
        assertThat(vo.getPurchaseOrders()).hasSize(expected);
        assertThat(vo.getInventoryQuantity()).hasSize(expected);
        assertThat(vo.getInboundQuantity()).hasSize(expected);
        assertThat(vo.getOutboundQuantity()).hasSize(expected);

        assertThat(vo.getFullDates().get(expected - 1))
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Shanghai")).toString());

        vo.getSales().forEach(v -> assertThat(v).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO));
        vo.getOrders().forEach(v -> assertThat(v).isNotNull().isGreaterThanOrEqualTo(0L));
        vo.getInboundQuantity().forEach(v -> assertThat(v).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO));
        vo.getOutboundQuantity().forEach(v -> assertThat(v).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO));
    }
}
