package net.lab1024.sa.admin.module.scm.order;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;

import net.lab1024.sa.admin.module.scm.common.ScmW3PgITBase;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.customer.controller.CustomerController;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerAddForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerQueryForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerSellerReassignForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerStatusForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerUpdateForm;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerVO;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerQueryService;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderAddressForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderLogQueryForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderRefundQueryForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnQueryForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderAddForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderItemForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderQueryForm;
import net.lab1024.sa.admin.module.scm.order.domain.vo.OrderAddressSnapshotVO;
import net.lab1024.sa.admin.module.scm.order.domain.vo.OrderOperationLogVO;
import net.lab1024.sa.admin.module.scm.order.domain.vo.OrderRefundVO;
import net.lab1024.sa.admin.module.scm.order.domain.vo.OrderReturnDetailVO;
import net.lab1024.sa.admin.module.scm.order.domain.vo.OrderReturnItemVO;
import net.lab1024.sa.admin.module.scm.order.domain.vo.OrderReturnVO;
import net.lab1024.sa.admin.module.scm.order.domain.vo.SalesOrderDetailVO;
import net.lab1024.sa.admin.module.scm.order.domain.vo.SalesOrderItemVO;
import net.lab1024.sa.admin.module.scm.order.domain.vo.SalesOrderVO;
import net.lab1024.sa.admin.module.scm.order.service.OrderRefundService;
import net.lab1024.sa.admin.module.scm.order.service.OrderReturnService;
import net.lab1024.sa.admin.module.scm.order.service.SalesOrderQueryService;
import net.lab1024.sa.admin.module.scm.order.service.SalesOrderService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.base.common.util.SmartRequestUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * P0-F 销售域（订单族 + 客户）的行级数据范围与其写入侧前提。
 *
 * <p>被测口径来自 {@code docs/decisions.md}「P0 基线收口裁决」第 2、4、6 条：
 * 销售默认只看 {@code customer.seller_id} / {@code sales_order.seller_id} 等于本人的行；
 * {@code seller_id IS NULL} 是「未分配」，普通销售看不见、持全量范围者看得见；
 * 放宽只能通过显式权限点；<b>新建客户的负责人由服务端强制为当前员工</b>——
 * 没有这条写入侧收口，行级范围就只是「藏起来」：谁都能先把客户建在别人名下再去读。
 *
 * <p><b>身份与权限怎么造</b>：范围解析读两处外部状态——登录员工
 * （{@code SmartRequestUtil}，用 {@link #as} 覆盖基类的员工 1）与功能权限
 * （{@code StpUtil.hasPermission}）。IT 线程里没有 Sa-Token 上下文，直接调 {@code StpUtil}
 * 会抛「SaTokenContext 未初始化」并被 {@code ScmDataScopeService#hasPermission} 吞成 false，
 * 因此这里按 {@code FileAccessGuardTest} 的既有做法用 {@code mockStatic(StpUtil.class)}
 * 显式给权限；未点名的权限码取 Mockito 默认值 false，正好等于失败关闭。
 * 测试员工一律 {@code administratorFlag=false}（裁决第 5 条：超管通过不构成权限证据）。
 *
 * <p><b>越权读的断言口径</b>：只断到异常类型与 {@link UserErrorCode#NO_PERMISSION} 的语义。
 * HTTP 信封里的数字码不在这里断：底座 {@code GlobalExceptionHandler} 对普通
 * {@code BusinessException} 统一按系统错误出码，30005 的映射归属底座，不在本域内改。
 *
 * <p><b>为什么订单夹具用直接 INSERT</b>：本类要证的是「列表与详情按负责人收窄」，
 * 订单状态机与金额在 {@code SalesOrderServiceIT} 已有覆盖，这里直插订单行只为造出
 * 「两个业务员各有一张单」的形状。客户与「能否对该客户开单」仍走真实领域服务，
 * 因为那正是要被测的写入侧逻辑。
 */
@DisplayName("P0-F 订单与客户数据范围（PG IT）")
class ScmOrderCustomerDataScopePgIT extends ScmW3PgITBase {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerQueryService customerQueryService;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private SalesOrderQueryService salesOrderQueryService;

    @Autowired
    private OrderReturnService orderReturnService;

    @Autowired
    private OrderRefundService orderRefundService;

    @Autowired
    private org.apache.ibatis.session.SqlSession session;

    /** 两个普通销售：各自拥有客户与订单，互不可见。 */
    private Long sellerA;
    private Long sellerB;

    @BeforeEach
    void setUpEmployees() {
        sellerA = newEmployee("A");
        sellerB = newEmployee("B");
    }

    // ==================== 1. 客户列表 / 详情 ====================

    @Test
    @DisplayName("客户列表：无全量范围时只返回 seller_id = 本人的行")
    void customerListIsNarrowedToOwnSeller() {
        Long customerOfA = createCustomer("CA", sellerA);
        Long customerOfB = createCustomer("CB", sellerB);

        assertThat(as(sellerA, Set.of(), () -> customerIds(customerList())))
                .contains(customerOfA).doesNotContain(customerOfB);
        assertThat(as(sellerB, Set.of(), () -> customerIds(customerList())))
                .contains(customerOfB).doesNotContain(customerOfA);
    }

    @Test
    @DisplayName("未分配客户：普通销售不可见，持 scm:customer:scope:all:query 者可见")
    void unassignedCustomerVisibleOnlyToAllScope() {
        Long unassigned = addCustomer(sellerA, Set.of(ScmDataScopeService.CUSTOMER_ASSIGN_PERM), "CU", null);
        assertThat(sellerIdOf(unassigned)).isNull();

        assertThat(as(sellerA, Set.of(), () -> customerIds(customerList()))).doesNotContain(unassigned);
        assertThat(as(sellerB, Set.of(), () -> customerIds(customerList()))).doesNotContain(unassigned);
        assertThat(as(sellerB, Set.of(ScmDataScopeService.CUSTOMER_ALL_PERM), () -> customerIds(customerList())))
                .contains(unassigned);
    }

    @Test
    @DisplayName("客户详情：别人的客户 30005，自己的照常返回；不存在仍是 40430")
    void customerDetailDeniesOutOfScopeRead() {
        Long customerOfA = createCustomer("DA", sellerA);

        assertThat(as(sellerA, Set.of(), () -> customerQueryService.detail(customerOfA).getCustomerId()))
                .isEqualTo(customerOfA);
        assertNoPermission(() -> as(sellerB, Set.of(), () -> customerQueryService.detail(customerOfA)));

        // 越权判定不得倒过来把「不存在」也报成无权限：那会让探测 id 与探测权限混成一种结果
        expectCode(() -> as(sellerB, Set.of(), () -> customerQueryService.detail(-1L)), 40430);

        // 全量范围者能读别人的详情（销售主管 / 财务口径）
        assertThat(as(sellerB, Set.of(ScmDataScopeService.CUSTOMER_ALL_PERM),
                () -> customerQueryService.detail(customerOfA).getCustomerId())).isEqualTo(customerOfA);
    }

    @Test
    @DisplayName("集团客户与 settle_mode=GROUP 不扩大可见范围")
    void groupRelationDoesNotWidenVisibility() {
        Long groupId = addCustomer(sellerA, Set.of(), "GP", null, form -> {
            form.setCustomerTypeId(customerTypeId("GROUP"));
            form.setSettleMode("GROUP");
        });
        // 乙名下的子公司挂在甲名下的集团上：归属只按本行 seller_id 判定，不做上溯/下钻展开
        Long childId = addCustomer(sellerB, Set.of(), "GC", null, form -> form.setParentCustomerId(groupId));

        assertThat(as(sellerB, Set.of(), () -> customerIds(customerList())))
                .contains(childId).doesNotContain(groupId);
        assertNoPermission(() -> as(sellerB, Set.of(), () -> customerQueryService.detail(groupId)));
        // 常购商品取的是成交价与用量，同属客户读边界
        assertNoPermission(() -> as(sellerB, Set.of(),
                () -> customerQueryService.frequentSkus(groupId, 90, 20)));

        assertThat(as(sellerA, Set.of(), () -> customerIds(customerList())))
                .contains(groupId).doesNotContain(childId);
        // 列表仍会补全上级名称（展示用），但这不等于能读上级客户的详情
        assertThat(as(sellerB, Set.of(), () -> customerQueryService.detail(childId).getParentCustomerName()))
                .isNotBlank();
    }

    // ==================== 2. 写入侧归属收口 ====================

    @Test
    @DisplayName("无分配权新建客户：seller_id 强制为本人，伪造的 sellerId 一律忽略")
    void createForcesSellerToCallerAndIgnoresSpoofedValue() {
        Long spoofed = addCustomer(sellerA, Set.of(), "W1", sellerB);

        assertThat(sellerIdOf(spoofed)).isEqualTo(sellerA);
        assertThat(as(sellerA, Set.of(), () -> customerIds(customerList()))).contains(spoofed);
        assertThat(as(sellerB, Set.of(), () -> customerIds(customerList()))).doesNotContain(spoofed);
    }

    @Test
    @DisplayName("编辑客户不得改派：表单里的 sellerId 对任何角色都只是回显值")
    void updateNeverReassignsSeller() {
        Long customerId = createCustomer("W2", sellerA);

        // 连分配权持有者都不能靠 /update 顺带改派——改派只有 reassignSeller 一条路
        as(sellerA, Set.of(ScmDataScopeService.CUSTOMER_ASSIGN_PERM), () -> {
            CustomerUpdateForm update = updateForm(customerId, 0);
            update.setSellerId(sellerB);
            customerService.update(update);
            return null;
        });

        assertThat(sellerIdOf(customerId)).isEqualTo(sellerA);
        assertThat(as(sellerB, Set.of(), () -> customerIds(customerList()))).doesNotContain(customerId);
    }

    @Test
    @DisplayName("改派走独立端点：权限码已种入菜单，带分配权可移动归属，版本过期 40921")
    void reassignSellerEndpointIsTheOnlyMovePath() throws Exception {
        Long customerId = createCustomer("W3", sellerA);

        Method endpoint = CustomerController.class.getDeclaredMethod("reassignSeller",
                CustomerSellerReassignForm.class);
        SaCheckPermission permission = endpoint.getAnnotation(SaCheckPermission.class);
        assertThat(permission).as("改派端点必须自带权限注解，不能只靠 Service 判断").isNotNull();
        assertThat(permission.value()).containsExactly(ScmDataScopeService.CUSTOMER_ASSIGN_PERM);

        // 权限点确实存在且只种了一次，并落在「销售主管有、普通销售没有」的角色上（V55 + V56）
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_menu WHERE api_perms = ?", Integer.class,
                ScmDataScopeService.CUSTOMER_ASSIGN_PERM)).isEqualTo(1);
        assertThat(roleHoldsPermission("SCM_SALES", ScmDataScopeService.CUSTOMER_ASSIGN_PERM)).isFalse();
        assertThat(roleHoldsPermission("SCM_SALES_LEAD", ScmDataScopeService.CUSTOMER_ASSIGN_PERM)).isTrue();

        // 版本比对与 update 同一口径：陈旧改派必须撞 40921，而不是覆盖别人的编辑
        expectCode(() -> as(sellerA, Set.of(ScmDataScopeService.CUSTOMER_ASSIGN_PERM), () -> {
            customerService.reassignSeller(reassignForm(customerId, sellerB, 99));
            return null;
        }), 40921);

        as(sellerB, Set.of(ScmDataScopeService.CUSTOMER_ASSIGN_PERM), () -> {
            customerService.reassignSeller(reassignForm(customerId, sellerB, sellerVersion(customerId)));
            return null;
        });

        assertThat(sellerIdOf(customerId)).isEqualTo(sellerB);
        assertThat(as(sellerB, Set.of(), () -> customerIds(customerList()))).contains(customerId);
        assertThat(as(sellerA, Set.of(), () -> customerIds(customerList()))).doesNotContain(customerId);
    }

    @Test
    @DisplayName("收回为未分配：只有分配权者可执行，执行后连本人也读不到")
    void unassignClearsOwnership() {
        Long customerId = createCustomer("W4", sellerA);

        as(sellerA, Set.of(ScmDataScopeService.CUSTOMER_ASSIGN_PERM), () -> {
            customerService.reassignSeller(reassignForm(customerId, null, sellerVersion(customerId)));
            return null;
        });

        assertThat(sellerIdOf(customerId)).isNull();
        assertThat(as(sellerA, Set.of(), () -> customerIds(customerList()))).doesNotContain(customerId);
    }

    @Test
    @DisplayName("无分配权者不能对读不到的客户开单：建单入口按同一范围拒绝")
    void orderCreateRejectsForeignCustomer() {
        Long customerOfB = createTradableCustomer("W5", sellerB);
        Long sku = newOnShelfSku("W5");

        assertNoPermission(() -> as(sellerA, Set.of(),
                () -> salesOrderService.create(orderForm(customerOfB, sku), prefix + "-w5")));

        // 负责人自己照常能开单，且订单负责人取自客户快照
        Long orderId = as(sellerB, Set.of(),
                () -> salesOrderService.create(orderForm(customerOfB, sku), prefix + "-w5b").getOrderId());
        assertThat(sellerIdOfOrder(orderId)).isEqualTo(sellerB);
    }

    @Test
    @DisplayName("分配权隐含开单例外：主管把客户指定给别人后可以替他录单")
    void assignRightAllowsOrderForOtherSellersCustomer() {
        Long customerOfB = createTradableCustomer("W6", sellerB);
        Long sku = newOnShelfSku("W6");

        Long orderId = as(sellerA, Set.of(ScmDataScopeService.CUSTOMER_ASSIGN_PERM),
                () -> salesOrderService.create(orderForm(customerOfB, sku), prefix + "-w6").getOrderId());
        assertThat(sellerIdOfOrder(orderId)).isEqualTo(sellerB);
    }

    // ==================== 3. 订单族列表与详情 ====================

    @Test
    @DisplayName("订单列表：无全量范围时只返回 seller_id = 本人的行；未分配单只对全量范围可见")
    void orderListIsNarrowedToOwnSeller() {
        Long customer = createCustomer("OA", sellerA);
        Long orderOfA = insertOrder(customer, "A", sellerA);
        Long orderOfB = insertOrder(customer, "B", sellerB);
        Long unassigned = insertOrder(customer, "N", null);

        assertThat(as(sellerA, Set.of(), () -> orderIds(orderList()))).containsExactly(orderOfA);
        assertThat(as(sellerA, Set.of(ScmDataScopeService.ORDER_ALL_PERM), () -> orderIds(orderList())))
                .contains(orderOfA, orderOfB, unassigned);
    }

    @Test
    @DisplayName("订单列表的 sellerId 筛选只能收窄，不能扩大")
    void sellerIdFilterNarrowsOnly() {
        Long customer = createCustomer("OF", sellerA);
        Long orderOfA = insertOrder(customer, "A", sellerA);
        Long orderOfB = insertOrder(customer, "B", sellerB);

        // 普通销售按别人的业务员筛选：范围谓词仍然 AND 在上面，结果必须为空
        assertThat(as(sellerA, Set.of(), () -> orderIds(salesOrderQueryService.query(sellerFilter(sellerB)))))
                .isEmpty();
        // 同一筛选交给全量范围者：收窄生效，只剩乙的单
        assertThat(as(sellerA, Set.of(ScmDataScopeService.ORDER_ALL_PERM),
                () -> orderIds(salesOrderQueryService.query(sellerFilter(sellerB))))).containsExactly(orderOfB);
        assertThat(as(sellerA, Set.of(ScmDataScopeService.ORDER_ALL_PERM),
                () -> orderIds(salesOrderQueryService.query(sellerFilter(sellerA))))).containsExactly(orderOfA);
    }

    @Test
    @DisplayName("订单详情：别人的单 30005，未分配单对普通销售同样 30005")
    void orderDetailDeniesOutOfScopeRead() {
        Long customer = createCustomer("OD", sellerA);
        Long orderOfA = insertOrder(customer, "A", sellerA);
        Long unassigned = insertOrder(customer, "N", null);

        assertThat(as(sellerA, Set.of(), () -> salesOrderQueryService.detail(orderOfA).getOrderId())).isEqualTo(orderOfA);
        assertNoPermission(() -> as(sellerB, Set.of(), () -> salesOrderQueryService.detail(orderOfA)));
        assertNoPermission(() -> as(sellerA, Set.of(), () -> salesOrderQueryService.detail(unassigned)));
        assertThat(as(sellerB, Set.of(ScmDataScopeService.ORDER_ALL_PERM),
                () -> salesOrderQueryService.detail(unassigned).getOrderId())).isEqualTo(unassigned);

        expectCode(() -> as(sellerB, Set.of(), () -> salesOrderQueryService.detail(-1L)), 40460);
    }

    @Test
    @DisplayName("退货单与退款单的列表和详情跟随父订单负责人范围")
    void returnAndRefundFollowParentOrderScope() {
        Long customer = createCustomer("OR", sellerA);
        Long orderOfA = insertOrder(customer, "A", sellerA);
        Long orderOfB = insertOrder(customer, "B", sellerB);
        Long returnOfA = insertReturn(orderOfA, customer, "A");
        Long returnOfB = insertReturn(orderOfB, customer, "B");
        Long refundOfA = insertRefund(returnOfA, orderOfA, customer, "A");
        Long refundOfB = insertRefund(returnOfB, orderOfB, customer, "B");

        assertThat(as(sellerB, Set.of(), () -> returnIds(orderReturnService.query(newForm(new OrderReturnQueryForm())))))
                .containsExactly(returnOfB);
        assertThat(as(sellerB, Set.of(), () -> orderReturnService.detail(returnOfB).getReturnId())).isEqualTo(returnOfB);
        assertNoPermission(() -> as(sellerB, Set.of(), () -> orderReturnService.detail(returnOfA)));

        assertThat(as(sellerA, Set.of(), () -> refundIds(orderRefundService.query(newForm(new OrderRefundQueryForm())))))
                .containsExactly(refundOfA);
        assertNoPermission(() -> as(sellerA, Set.of(), () -> orderRefundService.detail(refundOfB)));

        // 全量范围两类单都看得到（销售主管口径）
        assertThat(as(sellerB, Set.of(ScmDataScopeService.ORDER_ALL_PERM),
                () -> returnIds(orderReturnService.query(newForm(new OrderReturnQueryForm())))))
                .contains(returnOfA, returnOfB);
    }

    @Test
    @DisplayName("操作日志列表按父订单收窄：看不到订单就看不到它的日志")
    void orderLogListFollowsParentOrderScope() {
        Long customer = createCustomer("OL", sellerA);
        Long orderOfA = insertOrder(customer, "A", sellerA);
        Long orderOfB = insertOrder(customer, "B", sellerB);
        insertLog(orderOfA, "CREATE");
        insertLog(orderOfB, "CONFIRM");

        assertThat(as(sellerA, Set.of(), () -> logOrderIds(salesOrderQueryService.logs(newForm(new OrderLogQueryForm())))))
                .containsExactly(orderOfA);
        assertThat(as(sellerB, Set.of(), () -> logOrderIds(salesOrderQueryService.logs(newForm(new OrderLogQueryForm())))))
                .containsExactly(orderOfB);

        // 指名查别人的订单日志：不是「少几条」，而是一条都不给
        OrderLogQueryForm targeted = newForm(new OrderLogQueryForm());
        targeted.setOrderId(orderOfB);
        assertThat(as(sellerA, Set.of(), () -> salesOrderQueryService.logs(targeted).getList())).isEmpty();
    }

    // ==================== 4. 失败关闭的边界 ====================

    @Test
    @DisplayName("没有登录员工上下文：列表返回空分页，而不是全量")
    void missingIdentityFailsClosed() {
        createCustomer("F1", sellerA);
        SmartRequestUtil.remove();

        PageResult<CustomerVO> customers = customerQueryService.query(newForm(new CustomerQueryForm()));
        assertThat(customers.getList()).isEmpty();
        assertThat(customers.getTotal()).isZero();
        assertThat(customers.getEmptyFlag()).isTrue();

        PageResult<SalesOrderVO> orders = salesOrderQueryService.query(newForm(new SalesOrderQueryForm()));
        assertThat(orders.getList()).isEmpty();
        assertThat(orders.getTotal()).isZero();
    }

    @Test
    @DisplayName("订单族 VO 不携带成本列：本期无成本可抹，将来加列必须同时接成本权限")
    void orderVosExposeNoCostColumns() {
        for (Class<?> vo : List.of(SalesOrderVO.class, SalesOrderDetailVO.class, SalesOrderItemVO.class,
                OrderAddressSnapshotVO.class, OrderReturnVO.class, OrderReturnDetailVO.class,
                OrderReturnItemVO.class, OrderRefundVO.class, OrderOperationLogVO.class)) {
            for (Field field : vo.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                assertThat(field.getName())
                        .as("%s.%s 属于成本/毛利口径，必须按 scm:report:cost:query 抹成 null 才能暴露",
                                vo.getSimpleName(), field.getName())
                        .doesNotMatch("(?i).*(cost|margin|profit|purchaseprice).*");
            }
        }
    }

    // ==================== 夹具 ====================

    /**
     * 真实员工行：范围判定用的是 {@code seller_id} 指向的员工 id，因此按最小必要列插真行，
     * 靠测试事务回滚清理。{@code administrator_flag} 必须是 FALSE。
     */
    private Long newEmployee(String suffix) {
        String loginName = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder",
                "销售" + suffix);
        Long employeeId = jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
        assertThat(jdbc.queryForObject(
                "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                .as("正式业务角色的验收账号禁止超管位（裁决第 5 条）").isFalse();
        return employeeId;
    }

    private void loginAs(Long employeeId) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("SCM 数据范围 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        employee.setDepartmentId(1L);
        SmartRequestUtil.setRequestUser(employee);
    }

    /**
     * 以某个员工 + 一组功能权限执行一段逻辑。权限由 {@code mockStatic(StpUtil.class)} 提供：
     * 未点名的权限码取 Mockito 默认值 false，与生产上的失败关闭取向一致。
     */
    private <T> T as(Long employeeId, Set<String> permissions, Supplier<T> body) {
        loginAs(employeeId);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            permissions.forEach(p -> stp.when(() -> StpUtil.hasPermission(p)).thenReturn(true));
            return body.get();
        }
    }

    private void assertNoPermission(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .isNotInstanceOf(ScmBusinessException.class)
                .hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
    }

    /** 在当前登录员工下建客户；不提交 sellerId，归属由服务端强制为当前员工。 */
    private Long createCustomer(String suffix, Long ownerEmployeeId) {
        return addCustomer(ownerEmployeeId, Set.of(), suffix, null);
    }

    /** 同上，并把客户推进到可交易状态（订单写入侧要求 COOPERATING）。 */
    private Long createTradableCustomer(String suffix, Long ownerEmployeeId) {
        Long customerId = createCustomer(suffix, ownerEmployeeId);
        as(ownerEmployeeId, Set.of(), () -> {
            CustomerStatusForm status = new CustomerStatusForm();
            status.setCustomerId(customerId);
            status.setVersion(0);
            status.setStatus("COOPERATING");
            customerService.updateStatus(status);
            return null;
        });
        return customerId;
    }

    /**
     * 以 {@code callerEmployeeId} 身份 + {@code permissions} 提交建客户表单。
     *
     * @param submittedSellerId 客户端提交的归属；无分配权时必须被服务端覆盖成调用者本人
     */
    private Long addCustomer(Long callerEmployeeId, Set<String> permissions, String suffix, Long submittedSellerId) {
        return addCustomer(callerEmployeeId, permissions, suffix, submittedSellerId, form -> {
        });
    }

    private Long addCustomer(Long callerEmployeeId, Set<String> permissions, String suffix, Long submittedSellerId,
                             java.util.function.Consumer<CustomerAddForm> customize) {
        return as(callerEmployeeId, permissions, () -> {
            CustomerAddForm form = customerForm(suffix);
            form.setSellerId(submittedSellerId);
            customize.accept(form);
            return customerService.add(form);
        });
    }

    private CustomerAddForm customerForm(String suffix) {
        CustomerAddForm form = new CustomerAddForm();
        form.setCustomerCode(prefix + "-" + suffix);
        form.setName("范围测试" + suffix);
        form.setCustomerTypeId(customerTypeId("ENTERPRISE"));
        form.setSettleMode("INDEPENDENT");
        return form;
    }

    private CustomerUpdateForm updateForm(Long customerId, Integer version) {
        CustomerUpdateForm form = new CustomerUpdateForm();
        form.setCustomerId(customerId);
        form.setVersion(version);
        form.setCustomerCode(prefix + "-UPD");
        form.setName("编辑后名称");
        form.setCustomerTypeId(customerTypeId("ENTERPRISE"));
        form.setSettleMode("INDEPENDENT");
        return form;
    }

    private CustomerSellerReassignForm reassignForm(Long customerId, Long sellerId, Integer version) {
        CustomerSellerReassignForm form = new CustomerSellerReassignForm();
        form.setCustomerId(customerId);
        form.setSellerId(sellerId);
        form.setVersion(version);
        return form;
    }

    private SalesOrderAddForm orderForm(Long customerId, Long skuId) {
        SalesOrderAddForm form = new SalesOrderAddForm();
        form.setCustomerId(customerId);
        form.setOrderSource("ADMIN");
        form.setRemark("数据范围测试单");
        OrderAddressForm address = new OrderAddressForm();
        address.setReceiverName("收货人");
        address.setReceiverPhone("13800000000");
        address.setAddress("测试地址");
        form.setAddress(address);
        SalesOrderItemForm item = new SalesOrderItemForm();
        item.setSkuId(skuId);
        item.setOrderedQuantity("1.0000");
        item.setManualPriceOverride(false);
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    private Long sellerIdOf(Long customerId) {
        evicted();
        return jdbc.queryForObject("SELECT seller_id FROM customer WHERE id = ?", Long.class, customerId);
    }

    private Long sellerIdOfOrder(Long orderId) {
        evicted();
        return jdbc.queryForObject("SELECT seller_id FROM sales_order WHERE id = ?", Long.class, orderId);
    }

    private Integer sellerVersion(Long customerId) {
        evicted();
        return jdbc.queryForObject("SELECT version FROM customer WHERE id = ?", Integer.class, customerId);
    }

    /** 列表夹具一律带 {@code prefix} 自限：同一事务里库里还有别的用例与种子数据，不假设空库。 */
    private Long insertOrder(Long customerId, String suffix, Long sellerId) {
        String orderNo = prefix + "-SO" + suffix;
        jdbc.update("INSERT INTO sales_order (order_no, customer_id, customer_code_snapshot,"
                        + " customer_name_snapshot, order_source, status, ordered_total_amount,"
                        + " settlement_total_amount, settle_mode_snapshot, seller_id)"
                        + " VALUES (?, ?, ?, ?, 'ADMIN', 'DRAFT', 0, 0, 'INDEPENDENT', ?)",
                orderNo, customerId, "CODE-" + suffix, "范围测试客户", sellerId);
        Long orderId = jdbc.queryForObject("SELECT id FROM sales_order WHERE order_no = ?", Long.class, orderNo);
        evicted();
        return orderId;
    }

    private Long insertReturn(Long orderId, Long customerId, String suffix) {
        String returnNo = prefix + "-RT" + suffix;
        jdbc.update("INSERT INTO order_return (return_no, order_id, customer_id, status, reason)"
                + " VALUES (?, ?, ?, 'PENDING', '范围测试退货')", returnNo, orderId, customerId);
        return jdbc.queryForObject("SELECT id FROM order_return WHERE return_no = ?", Long.class, returnNo);
    }

    private Long insertRefund(Long returnId, Long orderId, Long customerId, String suffix) {
        String refundNo = prefix + "-RF" + suffix;
        jdbc.update("INSERT INTO order_refund (refund_no, return_id, order_id, customer_id, refund_amount, status)"
                + " VALUES (?, ?, ?, ?, 1.0000, 'PENDING')", refundNo, returnId, orderId, customerId);
        return jdbc.queryForObject("SELECT id FROM order_refund WHERE refund_no = ?", Long.class, refundNo);
    }

    /** operator 必须是「前缀:数字员工id」形态：{@code SalesOrderQueryService#logs} 按冒号取员工名。 */
    private void insertLog(Long orderId, String operationType) {
        jdbc.update("INSERT INTO order_operation_log (order_id, operation_type, operator, created_by)"
                + " VALUES (?, ?, ?, ?)", orderId, operationType, "admin:" + sellerA, "admin:" + sellerA);
    }

    private <T extends net.lab1024.sa.base.common.domain.PageParam> T newForm(T form) {
        form.setPageNum(1L);
        form.setPageSize(100L);
        return form;
    }

    private SalesOrderQueryForm sellerFilter(Long sellerId) {
        SalesOrderQueryForm form = newForm(new SalesOrderQueryForm());
        form.setSellerId(sellerId);
        return form;
    }

    private PageResult<CustomerVO> customerList() {
        return customerQueryService.query(newForm(new CustomerQueryForm()));
    }

    private PageResult<SalesOrderVO> orderList() {
        return salesOrderQueryService.query(newForm(new SalesOrderQueryForm()));
    }

    private List<Long> customerIds(PageResult<CustomerVO> page) {
        return page.getList().stream().map(CustomerVO::getCustomerId).toList();
    }

    private List<Long> orderIds(PageResult<SalesOrderVO> page) {
        return page.getList().stream().map(SalesOrderVO::getOrderId).toList();
    }

    private List<Long> returnIds(PageResult<OrderReturnVO> page) {
        return page.getList().stream().map(OrderReturnVO::getReturnId).toList();
    }

    private List<Long> refundIds(PageResult<OrderRefundVO> page) {
        return page.getList().stream().map(OrderRefundVO::getRefundId).toList();
    }

    private List<Long> logOrderIds(PageResult<OrderOperationLogVO> page) {
        return page.getList().stream().map(OrderOperationLogVO::getOrderId).toList();
    }

    private boolean roleHoldsPermission(String roleCode, String permission) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM t_role_menu rm
                JOIN t_role r ON r.role_id = rm.role_id
                JOIN t_menu m ON m.menu_id = rm.menu_id
                WHERE r.role_code = ? AND m.api_perms = ?""", Integer.class, roleCode, permission);
        return count != null && count > 0;
    }

    /** 同一测试事务共用一个 SqlSession，MyBatis 一级缓存会按「语句 + 参数」命中旧结果。 */
    private void evicted() {
        session.clearCache();
    }
}
