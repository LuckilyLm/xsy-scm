package com.xsy.scm.screen;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.inventory.domain.form.InventoryWarningQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryWarningVO;
import com.xsy.scm.inventory.service.InventoryWarningQueryService;
import com.xsy.scm.purchase.domain.form.PurchaseOrderAddForm;
import com.xsy.scm.purchase.service.PurchaseOrderService;
import com.xsy.scm.screen.controller.ScreenDataController;
import com.xsy.scm.screen.domain.vo.ScreenBusinessVO;
import com.xsy.scm.screen.domain.vo.ScreenGeoVO;
import com.xsy.scm.screen.domain.vo.ScreenInventoryVO;
import com.xsy.scm.screen.domain.vo.ScreenPurchaseVO;
import com.xsy.scm.screen.domain.vo.ScreenTrendVO;
import com.xsy.scm.screen.service.ScreenDataService;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class ScreenTest {

    @Nested
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
        @DisplayName("地理面板与经营面板共用同一份人口规则：收窄后两组数字仍恒等")
        void geoAndBusinessStayIdentical() {
            Long warehouse = newWarehouse("SGO");
            Long seller = scopedEmployee(warehouse);
            as(seller, Set.of(), () -> {
                fixture("SGO", "4.0000", "4.0000");
                fixture("SGP", "5.0000", "5.0000");
                return null;
            });
            // 覆盖度里的「未归属」差额必须仍然存在：区划未解析的客户不会被静默丢掉
            jdbc.update("UPDATE xsy_customer SET city_code = NULL, city_name = NULL WHERE seller_id = ?", seller);
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
            // 必须提交：采购指标只算已提交的单（草稿还没进入履约链路），本用例验的是范围不是状态。
            submitOrder(purchaseOrderService.create(form, prefix + ":" + suffix + ":po").getId());
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
            jdbc.update("INSERT INTO xsy_employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                    employeeId, warehouseId);
        }

        /** 余额行是既成的库存记录，直接落表：大屏只读它，不经收货入库链路。 */
        private void seedBalance(Long warehouseId, Long skuId, String quantity) {
            jdbc.update("INSERT INTO xsy_inventory_balance (warehouse_id, sku_id, unit, quantity, reserved_quantity, "
                            + "avg_cost, version, deleted, created_at, updated_at, created_by, updated_by) "
                            + "VALUES (?, ?, ?, ?, 0, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'it', 'it')",
                    warehouseId, skuId, DEFAULT_PURCHASE_UNIT, new BigDecimal(quantity));
            evictMybatisCache();
        }

        private String warehouseName(Long warehouseId) {
            return jdbc.queryForObject("SELECT name FROM xsy_warehouse WHERE id = ?", String.class, warehouseId);
        }

        private String customerName(Long customerId) {
            return jdbc.queryForObject("SELECT name FROM xsy_customer WHERE id = ?", String.class, customerId);
        }

        private void loginAs(Long employeeId) {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(employeeId);
            employee.setActualName("大屏数据范围 IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(false);
            employee.setDepartmentId(1L);
            XsyRequestUtil.setRequestUser(employee);
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

    @Nested
    class ScreenW6PgIT extends ScmW6PgITBase {
        @Nested
        @DisplayName("B7 数据大屏聚合服务（PG IT）")
        class ScmScreenDataIT {
            @Autowired
            private ScreenDataService screenDataService;

            @Autowired
            private InventoryWarningQueryService inventoryWarningQueryService;

            @Test
            @DisplayName("经营数据：字段非 null、非负且规则自洽")
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

                // 规则自洽：今日量是累计量的子集，不可能超过
                assertThat(vo.getTodayOrderCount()).isLessThanOrEqualTo(vo.getTotalOrderCount());
                assertThat(vo.getTodaySettlementAmount()).isLessThanOrEqualTo(vo.getTotalSettlementAmount());
                // 「今日成交客户」是客户总表的子集
                assertThat(vo.getTodayCustomerCount()).isLessThanOrEqualTo(vo.getCustomerCount());
                assertThat(vo.getTodaySupplierCount()).isLessThanOrEqualTo(vo.getSupplierCount());
            }

            @Test
            @DisplayName("库存数据：字段非 null、非负且规则自洽")
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
            @DisplayName("采购数据：字段非 null、非负且规则自洽")
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

                // 四档互斥且之和恰好等于总数（设计稿把缺货画成与预警并列的一段，占比要凑满 100%）。
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
                // 注意两边规则并不完全重合，所以只能断言夹逼而不是相等：
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
            @DisplayName("地理分布：省级上卷与市级气泡恒等，覆盖度自洽且与其它面板同规则")
            void geoDataRollupAndCoverageAreConsistent() {
                ScreenGeoVO geo = screenDataService.getGeoData();
                // 约定是「空数组而不是 null」：前端直接把它喂给 ECharts 的 series.data
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

                // 省级是市级上卷而不是第二次聚合，因此三档量与市数都必须守恒
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
                // 跨面板规则：地理分布的分母与经营/库存面板的总数必须是同一件事，仓库同样只算启用仓
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

                jdbc.update("UPDATE xsy_customer SET province_code = 330000, city_code = 330100 WHERE deleted = FALSE");
                jdbc.update("UPDATE xsy_customer SET province_code = 440000, city_code = 440100 WHERE id = ?",
                        customerInGuangzhou);
                jdbc.update("UPDATE xsy_supplier SET province_code = 330000, city_code = 330100 WHERE deleted = FALSE");
                jdbc.update("UPDATE xsy_warehouse SET province_code = 330000, city_code = 330100 WHERE deleted = FALSE");
                // 前置守卫：杭州桶为空会让下面所有 {@code - 1} 断言在别处失败，看不出真正的原因
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_customer WHERE deleted = FALSE "
                        + "AND id <> ?", Integer.class, customerInGuangzhou)).isGreaterThan(0);

                long customerTotal = jdbc.queryForObject("SELECT count(*) FROM xsy_customer WHERE deleted = FALSE", Long.class);
                long supplierTotal = jdbc.queryForObject("SELECT count(*) FROM xsy_supplier WHERE deleted = FALSE", Long.class);
                long warehouseTotal = jdbc.queryForObject("SELECT count(*) FROM xsy_warehouse "
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
                // 无供应商 / 无仓库的档位必须是 0：约定里没有 null，前端直接拿它做气泡尺寸
                assertThat(guangzhou.getSupplierCount()).isZero();
                assertThat(guangzhou.getWarehouseCount()).isZero();
                assertThat(hangzhou.getCityName()).isEqualTo("杭州市");
                assertThat(guangzhou.getProvinceName()).isEqualTo("广东省");

                // 省级上卷：省集与市集一致，数值来自同一批市级记录
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

                // 字典外编码：{@code located} 只问「有没有市码」，气泡却要 JOIN 字典才画得出来。
                // 于是这条客户被算进「已归属」，同时从图上消失 —— 差额只能在覆盖度里被看见，
                // 这正是 coverage 存在的理由，所以把它钉成断言而不是留在注释里。
                jdbc.update("UPDATE xsy_customer SET city_code = 999999 WHERE id = ?", customerInGuangzhou);
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
             * 断言八条序列与日期轴长度一致，且末点就是北京时间的今天。
             *
             * <p>末点日期这条断言是「今日时区」的回归点：按 UTC 日界会让窗口整体平移 8 小时，
             * 早上下的单被算到前一天，而序列长度看起来完全正常 —— 只看长度是抓不到的。
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

        @Nested
        @DisplayName("B7 数据大屏权限种子：V28 菜单 / 授权 / 代码一致性（PG IT）")
        class ScmScreenPermissionMigrationIT {
            private static final List<Long> SCREEN_MENU_IDS = List.of(900L, 901L);

            @Test
            @DisplayName("V28：菜单与授权就位，序列已推进")
            void menuSeedIsCompleteAndGrantedToRoleOne() {
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_menu WHERE menu_id IN (900, 901)",
                        Integer.class)).isEqualTo(2);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_menu WHERE menu_id = 901 AND perms_type = 1 "
                                + "AND api_perms = 'scm:screen:query'",
                        Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu WHERE role_id = 1 AND menu_id IN (900, 901)",
                        Integer.class)).isEqualTo(2);
                assertThat(jdbc.queryForObject(
                        "SELECT nextval(pg_get_serial_sequence('t_menu','menu_id')) > 901",
                        Boolean.class)).isTrue();
            }

            @Test
            @DisplayName("ScreenDataController 的每个权限码都必须在 t_menu.api_perms 中")
            void everyControllerPermissionExistsInMenuSeed() {
                Set<String> declared = new LinkedHashSet<>();
                for (Method method : ScreenDataController.class.getDeclaredMethods()) {
                    if (!Modifier.isPublic(method.getModifiers())) {
                        continue;
                    }
                    SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
                    assertThat(permission)
                            .as("ScreenDataController#%s 必须声明 @SaCheckPermission", method.getName())
                            .isNotNull();
                    declared.addAll(List.of(permission.value()));
                }
                for (String perm : declared) {
                    assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM t_menu WHERE api_perms = ?",
                            Integer.class, perm))
                            .as("权限码 %s 不在 t_menu.api_perms 中", perm)
                            .isEqualTo(1);
                }
            }
        }
    }
}
