package net.lab1024.sa.admin.module.scm.screen;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import cn.dev33.satoken.stp.StpUtil;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseOrderService;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenBusinessVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenGeoVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenInventoryVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenPurchaseVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenTrendVO;
import net.lab1024.sa.admin.module.scm.screen.service.ScreenDataService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * 数据大屏的正式数据范围（P0-H 裁决第 4 步）。
 *
 * <p>大屏此前是唯一没有接范围的 SCM 读路径：今天 {@code scm:screen:query} 只授超管所以没有泄漏路径，
 * 但它是业务列表的<b>聚合视图</b>，一旦把入口授给某个岗位，没收窄的面板就是把全公司的成交额、
 * 库存量和采购额交给只被授权看自己那一块的人 —— 而且聚合值比明细更容易被误当成「已经授权过的数据」。
 *
 * <p>本类按面板各自的事实维度取证，一个维度都没有的（供应商、SKU）反过来钉住「不收窄是决定，
 * 不是漏判」：
 * <ul>
 *   <li>库存面板 —— 仓库维度。未授权仓必须整行消失，而不是以「0 库存」的节点继续画在地图上。</li>
 *   <li>经营面板 —— 业务员维度（{@code customer.seller_id} / {@code sales_order.seller_id}）。</li>
 *   <li>采购面板 —— 采购归属 ∩ 仓库：只给全量采购范围的人仍然只能看到自己那个仓的量，
 *       与收货确认那条交集口径同源（裁决第 16 条）。</li>
 *   <li>地理面板 —— 与经营面板同一份人口片段，所以两侧数字必须恒等；只收一边就是两份 SQL 各自漂移。</li>
 *   <li>趋势面板 —— 每条序列各归各的维度；没有任何授权时得到的是「一片 0」而不是全量曲线。</li>
 * </ul>
 *
 * <p>夹具一律用<b>本用例新建</b>的仓库与<b>本人名下</b>的客户/单据，因此断言可以写精确值：
 * 大屏聚合的是全表，同库里其它数据只会通过「别人名下 / 别的仓」被范围谓词排除掉。
 */
@DisplayName("P0-H 数据大屏数据范围（PG IT）")
class ScmScreenDataScopePgIT extends ScmW5PgITBase {

    @Autowired
    private ScreenDataService screenDataService;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Test
    @DisplayName("库存面板按仓库收窄：未授权仓整行消失，不以 0 库存节点出现")
    void inventoryPanelsAreNarrowedByWarehouse() {
        Long warehouseA = newWarehouse("SIA");
        Long warehouseB = newWarehouse("SIB");
        Long skuA = newOnShelfSku("SIA");
        Long skuB = newOnShelfSku("SIB");
        seedBalance(warehouseA, skuA, "10.0000");
        seedBalance(warehouseB, skuB, "5.0000");
        Long onlyA = scopedEmployee(warehouseA);
        Long onlyB = scopedEmployee(warehouseB);

        ScreenInventoryVO viewA = as(onlyA, Set.of(), screenDataService::getInventoryData);
        assertThat(viewA.getTotalQuantity()).isEqualByComparingTo("10.0000");
        assertThat(viewA.getSkuCount()).isEqualTo(1L);
        assertThat(viewA.getWarehouseCount()).isEqualTo(1L);
        assertThat(viewA.getWarehouseDistribution()).hasSize(1);
        assertThat(viewA.getWarehouseNodes()).hasSize(1);
        assertThat(viewA.getWarehouseNodes().getFirst().getWarehouseName())
                .isEqualTo(warehouseName(warehouseA));
        // 四档互斥且之和等于总数，这条不变量在收窄后仍要成立（谓词落错分支就会破）
        assertThat(viewA.getHealth().getNormalCount() + viewA.getHealth().getLowCount()
                + viewA.getHealth().getHighCount() + viewA.getHealth().getUnconfiguredCount()
                + viewA.getHealth().getOutOfStockCount()).isEqualTo(viewA.getHealth().getTotalSkuCount());

        assertThat(as(onlyB, Set.of(), screenDataService::getInventoryData).getTotalQuantity())
                .isEqualByComparingTo("5.0000");
        // 全量范围者两边都看得到，且没有把两个仓并成一行
        ScreenInventoryVO viewAll = as(onlyA, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM),
                screenDataService::getInventoryData);
        assertThat(viewAll.getWarehouseDistribution()).extracting("warehouseName")
                .contains(warehouseName(warehouseA), warehouseName(warehouseB));
        assertThat(viewAll.getTotalQuantity().subtract(viewA.getTotalQuantity()))
                .isGreaterThanOrEqualTo(new BigDecimal("5.0000"));
    }

    @Test
    @DisplayName("没有任何仓库授权：大屏各项归零且列表为空，而不是回退成全量")
    void withoutAnyGrantTheScreenIsZeroed() {
        Long warehouse = newWarehouse("SIZ");
        seedBalance(warehouse, newOnShelfSku("SIZ"), "7.0000");
        Long nobody = newEmployee("SIZ0");

        ScreenInventoryVO inventory = as(nobody, Set.of(), screenDataService::getInventoryData);
        assertThat(inventory.getTotalQuantity()).isEqualByComparingTo("0");
        assertThat(inventory.getWarehouseNodes()).isEmpty();
        assertThat(inventory.getWarehouseDistribution()).isEmpty();
        assertThat(as(nobody, Set.of(), () -> screenDataService.getBusinessData().getCustomerCount())).isZero();
        assertThat(as(nobody, Set.of(), () -> screenDataService.getPurchaseData().getTotalPurchaseOrderCount()))
                .isZero();
        // 趋势仍要一天不少地占满 7 个点：缺数据不等于可以把日期轴缩短
        ScreenTrendVO trend = as(nobody, Set.of(), () -> screenDataService.getTrendData("7d"));
        assertThat(trend.getDates()).hasSize(7);
        assertThat(trend.getPurchaseOrders()).containsOnly(0L);
        assertThat(trend.getOrders()).containsOnly(0L);
    }

    @Test
    @DisplayName("经营面板按业务员收窄：自己名下的客户与订单，别人名下的一条不给")
    void businessPanelsFollowSellerOwner() {
        Long warehouse = newWarehouse("SIB2");
        Long sellerA = scopedEmployee(warehouse);
        Long sellerB = scopedEmployee(warehouse);

        as(sellerA, Set.of(), () -> {
            fixture("SBA", "4.0000", "4.0000");
            newCustomer();
            return null;
        });
        Fixture secondOfB = as(sellerB, Set.of(), () -> fixture("SBB", "6.0000", "6.0000"));

        ScreenBusinessVO viewA = as(sellerA, Set.of(), screenDataService::getBusinessData);
        assertThat(viewA.getCustomerCount()).as("本人名下两个客户").isEqualTo(2L);
        assertThat(viewA.getTotalOrderCount()).as("本人名下一张已确认订单").isEqualTo(1L);
        assertThat(viewA.getTotalSettlementAmount()).isGreaterThan(BigDecimal.ZERO);
        // 乙的客户与订单一条都不进甲的聚合
        assertThat(as(sellerB, Set.of(), () -> screenDataService.getBusinessData().getCustomerCount()))
                .isEqualTo(1L);
        assertThat(as(sellerB, Set.of(), screenDataService::getBusinessData).getTotalOrderCount()).isEqualTo(1L);
        // 客户排行同样按业务员：甲的榜单里不得出现乙的客户名
        assertThat(viewA.getTopCustomers()).extracting(ScreenBusinessVO.RankItem::getName)
                .doesNotContain(customerName(secondOfB.customerId()));

        // 供应商与 SKU 没有任何范围维度，按裁决保持团队共享读：两个人读到的是同一个全局数
        assertThat(as(sellerA, Set.of(), () -> screenDataService.getBusinessData().getSupplierCount()))
                .isEqualTo(as(sellerB, Set.of(), () -> screenDataService.getBusinessData().getSupplierCount()));
        assertThat(as(sellerA, Set.of(), () -> screenDataService.getBusinessData().getSkuCount())).isGreaterThan(0L);

        // 放宽是显式授权的结果：拿到客户全量范围后，乙的客户也进来了
        assertThat(as(sellerA, Set.of(ScmDataScopeService.CUSTOMER_ALL_PERM),
                screenDataService::getBusinessData).getCustomerCount())
                .isGreaterThan(viewA.getCustomerCount());
    }

    @Test
    @DisplayName("采购面板取交集：只有全量采购范围的人仍看不到别人仓的采购额")
    void purchasePanelRequiresPurchaserAndWarehouseTogether() {
        Long warehouseA = newWarehouse("SPA");
        Long warehouseB = newWarehouse("SPB");
        Long purchaserA = scopedEmployee(warehouseA);
        Long purchaserB = scopedEmployee(warehouseB);
        createPurchaseOrderAt(purchaserA, warehouseA, "SP-A");
        createPurchaseOrderAt(purchaserB, warehouseB, "SP-B");

        assertThat(as(purchaserA, Set.of(), screenDataService::getPurchaseData).getTodayPurchaseOrderCount())
                .isEqualTo(1L);
        // 只给采购全量范围：别人的采购单进了归属范围，但仍被仓库范围挡住 —— 两条是 AND，不是 OR
        ScreenPurchaseVO purchaseAllOnly = as(purchaserA, Set.of(ScmDataScopeService.PURCHASE_ALL_PERM),
                screenDataService::getPurchaseData);
        assertThat(purchaseAllOnly.getTodayPurchaseOrderCount()).isEqualTo(1L);
        assertThat(purchaseAllOnly.getTodayPurchaseAmount())
                .isEqualByComparingTo(as(purchaserA, Set.of(), screenDataService::getPurchaseData)
                        .getTodayPurchaseAmount());
        // 两条都补齐才看得到两仓之和
        assertThat(as(purchaserA, Set.of(ScmDataScopeService.PURCHASE_ALL_PERM,
                ScmDataScopeService.WAREHOUSE_ALL_PERM), screenDataService::getPurchaseData)
                .getTodayPurchaseOrderCount()).isGreaterThanOrEqualTo(2L);
    }

    @Test
    @DisplayName("地理面板与经营面板共用同一份人口口径：收窄后两组数字仍恒等")
    void geoAndBusinessStayIdentical() {
        Long warehouse = newWarehouse("SGO");
        Long seller = scopedEmployee(warehouse);
        as(seller, Set.of(), () -> {
            fixture("SGO", "4.0000", "4.0000");
            fixture("SGP", "5.0000", "5.0000");
            return null;
        });
        // 覆盖度里的「未归属」差额必须仍然存在：区划未解析的客户不会被静默丢掉
        jdbc.update("UPDATE customer SET city_code = NULL, city_name = NULL WHERE seller_id = ?", seller);
        evictMybatisCache();

        ScreenBusinessVO business = as(seller, Set.of(), screenDataService::getBusinessData);
        ScreenGeoVO geo = as(seller, Set.of(), screenDataService::getGeoData);
        ScreenInventoryVO inventory = as(seller, Set.of(), screenDataService::getInventoryData);

        assertThat(geo.getCoverage().getCustomerTotal()).isEqualTo(business.getCustomerCount());
        assertThat(geo.getCoverage().getSupplierTotal()).isEqualTo(business.getSupplierCount());
        assertThat(geo.getCoverage().getWarehouseTotal()).isEqualTo(inventory.getWarehouseCount());
        assertThat(geo.getCoverage().getCustomerLocated()).isZero();

        // 未授权的人：地理与经营同时归零，不会一个收了一个没收
        Long nobody = newEmployee("SGN");
        ScreenGeoVO empty = as(nobody, Set.of(), screenDataService::getGeoData);
        assertThat(empty.getCoverage().getCustomerTotal()).isZero();
        assertThat(as(nobody, Set.of(), screenDataService::getBusinessData).getCustomerCount()).isZero();
    }

    @Test
    @DisplayName("趋势各序列按自己的维度收窄：销售序列跟业务员、采购序列跟交集")
    void trendSeriesNarrowPerTheirOwnDimension() {
        Long warehouse = newWarehouse("STR");
        Long seller = scopedEmployee(warehouse);
        Long other = scopedEmployee(warehouse);
        as(seller, Set.of(), () -> fixture("ST1", "4.0000", "4.0000"));
        as(other, Set.of(), () -> fixture("ST2", "6.0000", "6.0000"));
        createPurchaseOrderAt(seller, warehouse, "ST-P");
        createPurchaseOrderAt(other, warehouse, "ST-Q");

        ScreenTrendVO mine = as(seller, Set.of(), () -> screenDataService.getTrendData("7d"));
        assertThat(sum(mine.getOrders())).as("只有本人名下那张已确认订单").isEqualTo(1L);
        assertThat(sum(mine.getPurchaseOrders())).isEqualTo(1L);
        assertThat(sum(otherTrend(other).getOrders())).isEqualTo(1L);
        // 销售序列不为 0，说明收窄来自归属谓词而不是「今天没有数据」
        assertThat(mine.getSales()).anyMatch(value -> value.signum() > 0);
    }

    // ==================== 夹具 ====================

    private ScreenTrendVO otherTrend(Long employeeId) {
        return as(employeeId, Set.of(), () -> screenDataService.getTrendData("7d"));
    }

    private long sum(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).sum();
    }

    private void createPurchaseOrderAt(Long caller, Long warehouseId, String suffix) {
        loginAs(caller);
        Long skuId = newOnShelfSku(suffix);
        Long supplierId = newPurchasableSupplier(suffix, skuId);
        PurchaseOrderAddForm form = orderForm(supplierId, warehouseId, skuId, "4.0000", "6.2000");
        purchaseOrderService.create(form, prefix + ":" + suffix + ":po");
    }

    private Long newEmployee(String suffix) {
        String loginName = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder",
                "大屏范围" + suffix);
        Long employeeId = jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
        assertThat(jdbc.queryForObject(
                "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                .as("超管通过不构成权限证据（裁决第 5 条）").isFalse();
        return employeeId;
    }

    private Long scopedEmployee(Long warehouseId) {
        // 后缀必须逐个不同：同一用例里两个员工授权同一个仓是本类的常用夹具，
        // 按仓库号取后缀会让第二个员工的 login_name 撞库，queryForObject 报「expected 1, actual 2」
        Long employeeId = newEmployee("E" + (++employeeSequence));
        grantWarehouseScope(employeeId, warehouseId);
        return employeeId;
    }

    private int employeeSequence;

    private void grantWarehouseScope(Long employeeId, Long warehouseId) {
        jdbc.update("INSERT INTO employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                employeeId, warehouseId);
    }

    /** 余额行是已验收的库存事实，直接落表：大屏只读它，不经收货入库链路。 */
    private void seedBalance(Long warehouseId, Long skuId, String quantity) {
        jdbc.update("INSERT INTO inventory_balance (warehouse_id, sku_id, unit, quantity, reserved_quantity, "
                        + "avg_cost, version, deleted, created_at, updated_at, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, 0, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'it', 'it')",
                warehouseId, skuId, DEFAULT_PURCHASE_UNIT, new BigDecimal(quantity));
        evictMybatisCache();
    }

    private String warehouseName(Long warehouseId) {
        return jdbc.queryForObject("SELECT name FROM warehouse WHERE id = ?", String.class, warehouseId);
    }

    private String customerName(Long customerId) {
        return jdbc.queryForObject("SELECT name FROM customer WHERE id = ?", String.class, customerId);
    }

    private void loginAs(Long employeeId) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("大屏数据范围 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        employee.setDepartmentId(1L);
        SmartRequestUtil.setRequestUser(employee);
    }

    /** 以某个员工 + 一组功能权限执行一段逻辑；不可嵌套（同一线程只能注册一次静态 mock）。 */
    private <T> T as(Long employeeId, Set<String> permissions, Supplier<T> body) {
        loginAs(employeeId);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            permissions.forEach(p -> stp.when(() -> StpUtil.hasPermission(p)).thenReturn(true));
            return body.get();
        }
    }
}
