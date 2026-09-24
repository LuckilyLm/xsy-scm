package net.lab1024.sa.admin.module.scm.delivery;

import cn.dev33.satoken.stp.StpUtil;
import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.common.handler.ScmExceptionHandler;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryDriverForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryOrdersForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryPrintOrdersForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryQueryForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryRouteForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryVersionForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.DeliveryOrderViewVO;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.DeliveryRouteVO;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryCandidateOrderQueryService;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryDriverService;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteQueryService;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteService;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseAddForm;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * 配送数据范围的 PostgreSQL 集成测试（P0-F 裁决第 9 条）。
 *
 * <p>钉住的口径：
 * <ul>
 *   <li>调度 / 线路规划员与司机不是同一个角色：司机维度经 {@code delivery_driver.employee_id}
 *       反查，没有绑定就是<b>零条线路</b>，不是全部线路；</li>
 *   <li>全量候选订单是调度能力，普通司机取不到；</li>
 *   <li>金额缺独立权限时由服务端抹成 {@code null}（前端渲染 {@code —}），绝不抹成 0；</li>
 *   <li>越权读取按「无权访问」处理，不是「记录不存在」；</li>
 *   <li>写侧的锁定读不受司机维度影响，否则主管的调度能力会被司机侧的读取收窄一起关掉。</li>
 * </ul>
 *
 * <p>Sa-Token 在非 Web 请求线程里没有会话，功能点判定只能桩 {@code StpUtil.hasPermission}
 * （与 {@code FileAccessGuardTest} 同一做法）；不桩即按「无权限」处理，正是失败关闭的一侧。
 */
@DisplayName("配送数据范围（PG IT）")
class ScmDeliveryDataScopePgIT extends ScmW5PgITBase {

    /** V43/V55 种下的功能点，必须与 {@code t_menu.api_perms} 逐字一致。 */
    private static final String ROUTE_PLAN = "scm:delivery:route:plan";
    private static final String DELIVERY_ALL = "scm:delivery:scope:all:query";
    private static final String AMOUNT = "scm:delivery:amount:query";
    private static final String WAREHOUSE_ALL = "scm:inventory:scope:all:query";

    /** 基类的下单夹具把收货地址写成这个值；客户地址必须与它完全一致，候选才回落得到客户坐标。 */
    private static final String ADDRESSED = "W5 IT 地址";

    @Autowired
    DeliveryDriverService driverService;
    @Autowired
    DeliveryRouteService routeService;
    @Autowired
    DeliveryRouteQueryService routeQuery;
    @Autowired
    DeliveryCandidateOrderQueryService candidateQuery;

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /** 直接插一名最小可用员工：范围判定只看 {@code employee_id} 与 {@code deleted_flag}。 */
    private Long newEmployee(String tag) {
        String loginName = (prefix + "-" + tag).toLowerCase(java.util.Locale.ROOT);
        Long departmentId = jdbc.queryForObject("SELECT department_id FROM t_employee ORDER BY employee_id LIMIT 1",
                Long.class);
        jdbc.update("INSERT INTO t_employee (login_name, login_pwd, actual_name, department_id, administrator_flag)"
                + " VALUES (?, 'it', ?, ?, FALSE)", loginName, tag, departmentId);
        return jdbc.queryForObject("SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
    }

    private void loginAs(Long employeeId) {
        var employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("范围 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        SmartRequestUtil.setRequestUser(employee);
    }

    /** 司机档案：启用必须带绑定，因此这里只在给出员工时启用。 */
    private Long newDriver(String tag, Long employeeId, String status) {
        var form = new DeliveryDriverForm();
        form.setDriverCode(prefix + "-" + tag);
        form.setDriverName("司机" + tag);
        form.setPhone("13800000000");
        form.setEmployeeId(employeeId);
        form.setStatus(status);
        return driverService.save(form);
    }

    private Long newWarehouse() {
        var warehouse = new WarehouseAddForm();
        warehouse.setWarehouseCode(prefix + "-WH");
        warehouse.setName("范围 IT 仓");
        warehouse.setLongitude(new BigDecimal("113.9"));
        warehouse.setLatitude(new BigDecimal("22.5"));
        warehouse.setGeomCrs("GCJ02");
        return warehouseService.create(warehouse);
    }

    private Long newRoute(Long warehouseId, Long driverId) {
        var form = new DeliveryRouteForm();
        form.setRouteName("范围 IT 线路");
        form.setDeliveryDate(LocalDate.now());
        form.setWarehouseId(warehouseId);
        form.setDriverId(driverId);
        return routeService.create(form);
    }

    /** 一个地址与坐标齐备的客户：停靠点坐标来自下单时冻结的地址快照，故必须在下单前改客户地址。 */
    private Long newLocatedCustomer() {
        Long customerId = newCustomer();
        jdbc.update("UPDATE customer SET address=?,longitude=113.94,latitude=22.54,geom_crs='GCJ02' WHERE id = ?",
                ADDRESSED, customerId);
        return customerId;
    }

    /** 组单：走写侧路径把订单编进线路，线路版本按原始行回读，不依赖被测的读取门禁。 */
    private Long orderInRoute(Long routeId, Long customerId, Long skuId) {
        return attach(routeId, sortedOrder(customerId, skuId));
    }

    /**
     * 已确认且**分拣已完成**的订单 —— P1 之后这是配送候选的硬前置（裁决第 11 条与补充第 18 条）。
     * 分拣这一步必须在有仓库授权的身份下做，因此需要时与 {@link #attach} 分开调用。
     */
    private Long sortedOrder(Long customerId, Long skuId) {
        Long orderId = confirmedSalesOrder(customerId, skuId, "1.0000", "1.0000");
        sortingCompletedFor(orderId);
        return orderId;
    }

    private Long attach(Long routeId, Long orderId) {
        var form = new DeliveryOrdersForm();
        form.setVersion(versionOf(routeId));
        form.setOrderIds(List.of(orderId));
        form.setReason("组单");
        routeService.addOrders(routeId, form);
        return orderId;
    }

    /** 确认规划：只有 PLANNED 及以后的线路可以出打印单据。 */
    private void plan(Long routeId) {
        var form = new DeliveryVersionForm();
        form.setVersion(versionOf(routeId));
        form.setReason("规划");
        routeService.plan(routeId, form);
    }

    private Integer versionOf(Long routeId) {
        return jdbc.queryForObject("SELECT version FROM delivery_route WHERE id = ?", Integer.class, routeId);
    }

    private DeliveryPrintOrdersForm printForm(Long routeId, Long orderId) {
        var form = new DeliveryPrintOrdersForm();
        form.setVersion(versionOf(routeId));
        form.setOrderIds(List.of(orderId));
        return form;
    }

    private String key(String tag) {
        return prefix + ":print:" + tag + ":" + System.nanoTime();
    }

    private List<Long> routeIds(List<DeliveryRouteVO> rows) {
        return rows.stream().map(DeliveryRouteVO::getId).toList();
    }

    /** 只授予给定功能点，其余一律 false：与 {@code ScmDataScopeService} 的失败关闭取向一致。 */
    private MockedStatic<StpUtil> grant(String... permissions) {
        var granted = List.of(permissions);
        var stp = mockStatic(StpUtil.class);
        stp.when(() -> StpUtil.hasPermission(anyString())).thenAnswer(call -> granted.contains(call.getArgument(0)));
        return stp;
    }

    /**
     * 越权读取必须是「无权访问」，不能是「记录不存在」。
     *
     * <p>抛的是 {@link ScmDataScopeException} 而不是裸 {@code BusinessException}：只有前者会被
     * {@link ScmExceptionHandler} 翻成 30005 信封，信封这一半必须在域内取证，
     * 否则类型改回去也没人发现（与库存侧同一做法）。
     */
    private void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isExactlyInstanceOf(ScmDataScopeException.class)
                .hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
        var envelope = new ScmExceptionHandler().handleDataScope(new ScmDataScopeException());
        assertThat(envelope.getCode()).isEqualTo(UserErrorCode.NO_PERMISSION.getCode());
        assertThat(envelope.getOk()).isFalse();
    }

    // ------------------------------------------------------------------
    // 线路范围
    // ------------------------------------------------------------------

    @Test
    @DisplayName("绑定了司机的员工只看得到自己名下的线路，别人的与未分配的都看不到")
    void employeeSeesOnlyRoutesOfBoundDriver() {
        Long a = newEmployee("A");
        Long b = newEmployee("B");
        Long d1 = newDriver("D1", a, "ENABLED");
        Long d2 = newDriver("D2", b, "ENABLED");
        Long warehouse = newWarehouse();
        Long r1 = newRoute(warehouse, d1);
        Long r2 = newRoute(warehouse, d2);
        Long r3 = newRoute(warehouse, null);

        loginAs(a);
        try (var stp = grant()) {
            assertThat(routeIds(routeQuery.query(new DeliveryQueryForm()).getList())).containsExactly(r1);
            assertThat(routeQuery.detail(r1)).isNotNull();
            assertDenied(() -> routeQuery.detail(r2));
            // 未分配司机的线路不属于任何司机，普通司机因此也读不到它。
            assertDenied(() -> routeQuery.detail(r3));
            assertDenied(() -> routeQuery.orderView(r2));
            assertDenied(() -> routeQuery.customerView(r2));
            assertDenied(() -> routeQuery.print(r2));
        }

        loginAs(b);
        try (var stp = grant()) {
            assertThat(routeIds(routeQuery.query(new DeliveryQueryForm()).getList())).containsExactly(r2);
        }

        // 持「全部配送数据」的调度：三条线路（含未分配）都在范围内。
        // 用 contains 而不是 containsExactly：全量范围按定义要看得到库里别人的线路，
        // 断言「自己的三条（含 driver 为空的 r3）都在」才是本用例的主题；
        // 「普通司机只能看到自己那一条」由上面的 containsExactly 负责，收窄语义没有被削弱。
        loginAs(a);
        try (var stp = grant(DELIVERY_ALL)) {
            assertThat(routeIds(routeQuery.query(new DeliveryQueryForm()).getList()))
                    .contains(r1, r2, r3);
            assertThat(routeQuery.detail(r3)).isNotNull();
        }
    }

    @Test
    @DisplayName("没有司机授权的登录人得到空分页，而不是全量线路")
    void unboundCallerGetsEmptyPageNotEverything() {
        Long owner = newEmployee("C");
        Long warehouse = newWarehouse();
        Long route = newRoute(warehouse, newDriver("D3", owner, "ENABLED"));
        Long existing = newRoute(warehouse, null);

        // 既没绑司机也没有全量授权的登录人（例如销售岗）：列表短路成空分页，详情按无权访问拒绝。
        loginAs(newEmployee("D"));
        try (var stp = grant()) {
            var page = routeQuery.query(new DeliveryQueryForm());
            assertThat(page.getTotal()).isZero();
            assertThat(page.getList()).isEmpty();
            assertDenied(() -> routeQuery.detail(route));
            // 真的不存在的线路仍是 41100，不能被越权判定吞成 30005。
            expectCode(() -> routeQuery.detail(existing + 999999L), 41100);
        }
    }

    // ------------------------------------------------------------------
    // 司机绑定
    // ------------------------------------------------------------------

    @Test
    @DisplayName("启用司机前必须绑定员工；停用可以留空，绑定不存在或已删除员工一律拒绝")
    void enabledDriverRequiresBinding() {
        var unbound = new DeliveryDriverForm();
        unbound.setDriverCode(prefix + "-N1");
        unbound.setDriverName("未绑定司机");
        unbound.setPhone("13800000000");
        unbound.setStatus("ENABLED");
        expectCode(() -> driverService.save(unbound), 41113);

        // 停用态允许留空：外部司机与历史行仍可作为资料存在，只是承担不了配送范围判定。
        unbound.setStatus("DISABLED");
        assertThat(driverService.save(unbound)).isNotNull();

        // 指向不存在的员工：关系列不建外键，只能在写入侧挡。
        unbound.setDriverCode(prefix + "-N2");
        unbound.setEmployeeId(999999999L);
        unbound.setStatus("ENABLED");
        expectCode(() -> driverService.save(unbound), 41114);

        // 员工被软删后，新的绑定同样必须失败。
        Long deleted = newEmployee("E");
        jdbc.update("UPDATE t_employee SET deleted_flag = TRUE WHERE employee_id = ?", deleted);
        unbound.setDriverCode(prefix + "-N3");
        unbound.setEmployeeId(deleted);
        expectCode(() -> driverService.save(unbound), 41114);

        unbound.setDriverCode(prefix + "-N4");
        unbound.setEmployeeId(newEmployee("F"));
        assertThatCode(() -> driverService.save(unbound)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("一个员工绑第二个活动司机：服务给出明确错误码，唯一索引自己也能挡住")
    void oneEmployeeCannotBindTwoActiveDrivers() {
        Long employee = newEmployee("G");
        Long first = newDriver("D4", employee, "ENABLED");

        // 裸 SQL 探针必须排在服务调用之前：服务那一步靠真 INSERT 撞唯一索引，PostgreSQL 会把
        // 整个测试事务置为 aborted，之后连 setSavepoint 都会连带失败（25P02）。
        // 约束不依赖 Java：绕过服务直接写同一张表也必须在库内失败。
        Long unbound = newDriver("D5", null, "DISABLED");
        expectSqlFailure("UPDATE delivery_driver SET employee_id = ? WHERE id = ?", employee, unbound);
        expectSqlFailure("INSERT INTO delivery_driver (driver_code, driver_name, phone, status, employee_id)"
                + " VALUES (?, ?, ?, 'ENABLED', ?)", prefix + "-D6", "绕过服务司机", "13800000002", employee);
        // 探针行随各自的 SAVEPOINT 回滚：留着第二行会让后面按员工反查线路的用例拿到错的角色
        assertThat(jdbc.queryForList("SELECT id FROM delivery_driver WHERE employee_id = ? AND deleted = FALSE",
                Long.class, employee)).containsExactly(first);

        var second = new DeliveryDriverForm();
        second.setDriverCode(prefix + "-D7");
        second.setDriverName("重复绑定司机");
        second.setPhone("13800000001");
        second.setEmployeeId(employee);
        second.setStatus("ENABLED");
        expectCode(() -> driverService.save(second), 41115);
    }

    // ------------------------------------------------------------------
    // 候选订单
    // ------------------------------------------------------------------

    @Test
    @DisplayName("候选池要求组单权与授权仓库：普通司机拿不到全量候选")
    void candidatesRequireDispatchRight() {
        Long sku = newOnShelfSku("SCOPE");
        Long customer = newLocatedCustomer();
        Long confirmed = confirmedSalesOrder(customer, sku, "1.0000", "1.0000");
        // 候选池的门槛里有「分拣已完成」这一条（P1 补充第 18 条），前置放在换身份之前做。
        sortingCompletedFor(confirmed);
        loginAs(newEmployee("H"));

        var filter = new DeliveryQueryForm();
        filter.setCustomerId(customer);

        // 只有线路查询身份的司机（无组单权）：连候选池的入口都没有。
        try (var stp = grant()) {
            assertDenied(() -> candidateQuery.query(filter));
        }

        // 有组单权但一个授权仓库都没有：不给候选行。
        try (var stp = grant(ROUTE_PLAN)) {
            assertThat(candidateQuery.query(filter).getList()).isEmpty();
        }

        // 组单权 + 仓库范围才看得见待排线订单；地址与电话在调度范围内完整给出。
        try (var stp = grant(ROUTE_PLAN, WAREHOUSE_ALL, AMOUNT)) {
            assertThat(candidateQuery.query(filter).getList())
                    .singleElement()
                    .satisfies(row -> {
                        assertThat(row.getOrderId()).isEqualTo(confirmed);
                        assertThat(row.getAddress()).isEqualTo(ADDRESSED);
                        assertThat(row.getReceiverPhone()).isNotBlank();
                        assertThat(row.getOrderAmount()).isNotNull();
                    });
        }
    }

    // ------------------------------------------------------------------
    // 金额与地址
    // ------------------------------------------------------------------

    @Test
    @DisplayName("缺金额权限时配送金额一律为 null（不是 0），有权限时才给出数字")
    void amountsAreHiddenWithoutPermission() {
        Long employee = newEmployee("I");
        Long driver = newDriver("D7", employee, "ENABLED");
        Long warehouse = newWarehouse();
        Long route = newRoute(warehouse, driver);
        Long orderId = orderInRoute(route, newLocatedCustomer(), newOnShelfSku("AMT"));
        plan(route);

        loginAs(employee);
        try (var stp = grant()) {
            var detail = routeQuery.detail(route);
            assertThat(detail.getRoute().getTotalAmount()).isNull();
            assertThat(detail.getStops()).allSatisfy(stop -> assertThat(stop.getTotalAmount()).isNull());
            assertThat(detail.getOrders()).allSatisfy(row -> assertThat(row.getOrderAmountSnapshot()).isNull());
            assertThat(routeQuery.orderView(route)).singleElement()
                    .satisfies(row -> assertThat(row.getOrderAmount()).isNull());
            assertThat(routeQuery.customerView(route)).singleElement()
                    .satisfies(row -> assertThat(row.getTotalAmount()).isNull());
            // 打印预览与正式生成里的金额同属一个口径，否则一条打印就绕过了字段级收口。
            var print = routeQuery.print(route);
            assertThat(print.getDetail().getRoute().getTotalAmount()).isNull();
            print.getItems().forEach(item -> assertThat(item.getOrderedLineAmount()).isNull());
            assertThat(routeService.printOrders(route, printForm(route, orderId), key("hidden")).getTotalAmount())
                    .isNull();
        }

        try (var stp = grant(AMOUNT)) {
            var detail = routeQuery.detail(route);
            assertThat(detail.getRoute().getTotalAmount()).isNotNull();
            assertThat(detail.getStops()).allSatisfy(stop -> assertThat(stop.getTotalAmount()).isNotNull());
            assertThat(routeQuery.orderView(route)).singleElement()
                    .satisfies(row -> assertThat(row.getOrderAmount()).isNotNull());
            assertThat(routeService.printOrders(route, printForm(route, orderId), key("shown")).getTotalAmount())
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("司机能读自己线路上订单的配送地址与电话，别人线路的整条读取都被拒")
    void driverReadsAddressOnlyInsideOwnRoute() {
        Long own = newEmployee("J");
        Long otherEmployee = newEmployee("K");
        Long warehouse = newWarehouse();
        Long ownRoute = newRoute(warehouse, newDriver("D8", own, "ENABLED"));
        Long otherRoute = newRoute(warehouse, newDriver("D9", otherEmployee, "ENABLED"));
        Long customer = newLocatedCustomer();
        orderInRoute(ownRoute, customer, newOnShelfSku("ADDR"));
        orderInRoute(otherRoute, customer, newOnShelfSku("ADDR2"));

        loginAs(own);
        try (var stp = grant()) {
            assertThat(routeQuery.orderView(ownRoute)).singleElement().satisfies(row -> {
                assertThat(row.getAddress()).isEqualTo(ADDRESSED);
                assertThat(row.getCustomerName()).isNotBlank();
            });
            // 停靠点上的收货电话是配送作业必需，随本人线路给出。
            assertThat(routeQuery.detail(ownRoute).getStops()).allSatisfy(stop -> {
                assertThat(stop.getAddressSnapshot()).isEqualTo(ADDRESSED);
                assertThat(stop.getReceiverPhoneSnapshot()).isNotBlank();
            });
            assertDenied(() -> routeQuery.orderView(otherRoute));
        }
    }

    // ------------------------------------------------------------------
    // 写侧不受司机维度影响
    // ------------------------------------------------------------------

    @Test
    @DisplayName("写侧的锁定读不受司机维度收窄：主管不会因为司机侧读取限制而失去调度能力")
    void mutationReadsStayUnscoped() {
        Long driverEmployee = newEmployee("L");
        Long warehouse = newWarehouse();
        Long ownRoute = newRoute(warehouse, newDriver("D10", driverEmployee, "ENABLED"));
        Long foreignRoute = newRoute(warehouse, null);

        // 分拣前置放在默认身份下做：司机侧账号没有仓库授权，写不了分拣任务，
        // 而本用例要断的是「组单这条写路径不受司机维度收窄」，不是分拣权。
        Long sortedOrder = sortedOrder(newLocatedCustomer(), newOnShelfSku("WRITE"));
        loginAs(driverEmployee);
        try (var stp = grant()) {
            // 读侧：未分配司机的线路越权。
            assertDenied(() -> routeQuery.detail(foreignRoute));
            // 写侧：组单要锁线路、读候选、重排停靠点，任何一步被司机维度挡掉都会静默废掉调度。
            attach(foreignRoute, sortedOrder);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM delivery_route_order WHERE route_id = ?",
                    Integer.class, foreignRoute)).isEqualTo(1);
            assertThat(routeQuery.detail(ownRoute)).isNotNull();
        }
    }
}
