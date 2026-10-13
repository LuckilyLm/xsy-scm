package com.xsy.scm.order.service;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.common.domain.entity.ScmIdempotencyRecordEntity;
import com.xsy.scm.common.error.ScmErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyRequestHasher;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.finance.constant.ScmFinanceReceivableSourceTypeEnum;
import com.xsy.scm.order.constant.OrderErrorCode;
import com.xsy.scm.order.constant.ScmOrderOperationTypeEnum;
import com.xsy.scm.order.constant.ScmOrderSourceEnum;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.form.OrderPoolAssignForm;
import com.xsy.scm.order.domain.form.OrderPoolClaimForm;
import com.xsy.scm.order.domain.form.OrderPoolQueryForm;
import com.xsy.scm.order.domain.vo.OrderPoolAssigneeVO;
import com.xsy.scm.order.domain.vo.SalesOrderVO;
import com.xsy.scm.order.manager.OrderOperationLogRecorder;
import com.xsy.scm.order.permission.OrderPermission;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.lab1024.sa.admin.module.system.employee.dao.EmployeeDao;
import net.lab1024.sa.admin.module.system.employee.domain.entity.EmployeeEntity;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.admin.module.system.login.manager.LoginManager;
import net.lab1024.sa.base.common.domain.UserPermission;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/**
 * 未分配订单池的服务层不变量：权限闸门、准入条件、乐观锁与条件更新的分工、
 * 目标业务员可用性，以及幂等指纹对版本前进的稳定性。
 *
 * <p>
 * 全部依赖都用 Mockito 替身，不启 Spring 上下文也不连库：这里要钉的是「哪些输入被拒绝、
 * 拒绝发生在哪一步、成功时写下了什么事实」，行锁与条件更新在数据库上的真实行为由集成测试负责。
 */
@DisplayName("未分配订单池：领取与指派的服务层规则")
class OrderPoolServiceTest {

    private static final long SUPERVISOR = 1L;
    private static final long ORDER_ID = 10L;
    private static final long TARGET_SELLER = 7L;
    /** ScmOperator 渲染成「用户类型值:用户 id」，员工类型值是 1。 */
    private static final String OPERATOR = "1:1";
    private static final String IDEMPOTENCY_KEY = "pool-key-1";

    /**
     * 候选业务员查询在服务里直接拼 LambdaQueryWrapper，列名解析依赖 MyBatis-Plus 的实体元数据缓存；
     * 纯单测没有 Spring 上下文去注册它，这里显式初始化一次。
     */
    @BeforeAll
    static void initMybatisPlusMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EmployeeEntity.class);
    }

    private final SalesOrderDao salesOrderDao = mock(SalesOrderDao.class);
    private final SalesOrderQueryService queryService = mock(SalesOrderQueryService.class);
    private final EmployeeDao employeeDao = mock(EmployeeDao.class);
    private final LoginManager loginManager = mock(LoginManager.class);
    private final OrderIdempotencyService idempotency = mock(OrderIdempotencyService.class);
    private final OrderOperationLogRecorder operationLogs = mock(OrderOperationLogRecorder.class);
    private final ScmDataScopeService dataScopeService = mock(ScmDataScopeService.class);

    private final OrderPoolService service = new OrderPoolService(salesOrderDao, queryService, employeeDao,
            loginManager, idempotency, operationLogs, dataScopeService);

    private final ScmIdempotencyRequestHasher hasher = new ScmIdempotencyRequestHasher(new ObjectMapper());

    @BeforeEach
    void setUp() {
        signIn(SUPERVISOR, true);
        var record = new ScmIdempotencyRecordEntity();
        record.setResultData(new LinkedHashMap<>());
        when(idempotency.claim(anyString(), anyString(), any()))
                .thenReturn(new OrderIdempotencyService.Claim(record, false));
    }

    @AfterEach
    void tearDown() {
        SmartRequestUtil.remove();
    }

    // ---------------------------------------------------------------- 权限闸门

    @Test
    @DisplayName("非超管且没有指派权限时拒绝访问，且在读库之前就失败")
    void nonSupervisorWithoutAssignPermissionIsRejectedBeforeAnyRead() {
        signIn(SUPERVISOR, false);
        try (MockedStatic<StpUtil> permissions = mockStatic(StpUtil.class)) {
            assertThatThrownBy(() -> service.unassigned(queryForm()))
                    .isInstanceOf(ScmDataScopeException.class);
            assertThatThrownBy(() -> service.assignees()).isInstanceOf(ScmDataScopeException.class);
            assertThatThrownBy(() -> service.assign(assignForm(ORDER_ID, 3, TARGET_SELLER), IDEMPOTENCY_KEY))
                    .isInstanceOf(ScmDataScopeException.class);
        }
        verifyNoInteractions(salesOrderDao, employeeDao, idempotency, operationLogs);
    }

    @Test
    @DisplayName("持有指派权限的业务员可以读池，超管不必再持有该权限")
    void assignPermissionOrSupervisorGrantsAccess() {
        signIn(SUPERVISOR, false);
        var form = queryForm();
        SalesOrderEntity row = mallOrder(ORDER_ID, 3, null);
        SalesOrderVO rowView = new SalesOrderVO();
        rowView.setOrderId(ORDER_ID);
        when(salesOrderDao.unassignedMallOrders(any(), eq(form))).thenReturn(List.of(row));
        when(queryService.order(row)).thenReturn(rowView);

        try (MockedStatic<StpUtil> permissions = mockStatic(StpUtil.class)) {
            permissions.when(() -> StpUtil.hasPermission(OrderPermission.ASSIGN)).thenReturn(true);
            assertThat(service.unassigned(form).getList()).extracting(SalesOrderVO::getOrderId)
                    .containsExactly(ORDER_ID);
        }

        signIn(SUPERVISOR, true);
        when(salesOrderDao.unassignedMallOrders(any(), eq(form))).thenReturn(List.of(row));
        assertThat(service.unassigned(form).getList()).extracting(SalesOrderVO::getOrderId)
                .containsExactly(ORDER_ID);
    }

    @Test
    @DisplayName("解析不到登录员工时失败关闭")
    void missingEmployeeIdentityFailsClosed() {
        var denied = mock(ScmDataScopeContext.class);
        when(denied.getEmployeeId()).thenReturn(null);
        when(dataScopeService.resolve()).thenReturn(denied);
        assertThatThrownBy(() -> service.assignees()).isInstanceOf(ScmDataScopeException.class);
    }

    // ---------------------------------------------------------------- 领取

    @Test
    @DisplayName("领取把订单指派给当前登录的业务员本人")
    void claimAssignsToTheSignedInOperator() {
        SalesOrderEntity order = mallOrder(ORDER_ID, 3, null);
        when(salesOrderDao.lock(ORDER_ID)).thenReturn(order);
        when(employeeDao.selectById(SUPERVISOR)).thenReturn(employee(SUPERVISOR, "主管", false, false, false));
        when(loginManager.getUserPermission(SUPERVISOR)).thenReturn(permission(OrderPermission.QUERY));
        when(salesOrderDao.assignUnassignedMallOrder(ORDER_ID, 3, SUPERVISOR, OPERATOR)).thenReturn(1);
        stubSnapshot(order);

        var form = new OrderPoolClaimForm();
        form.setOrderId(ORDER_ID);
        form.setVersion(3);
        service.claim(form, IDEMPOTENCY_KEY);

        verify(salesOrderDao).assignUnassignedMallOrder(ORDER_ID, 3, SUPERVISOR, OPERATOR);
    }

    // ---------------------------------------------------------------- 准入条件

    @Test
    @DisplayName("订单不存在、非商城单或已进入终态都按「订单不存在」拒绝")
    void onlyUnassignedLiveMallOrdersAreEligible() {
        when(salesOrderDao.lock(ORDER_ID)).thenReturn(null);
        assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_NOT_FOUND);

        when(salesOrderDao.lock(ORDER_ID))
                .thenReturn(orderOf(ScmOrderSourceEnum.ADMIN, ScmOrderStatusEnum.DRAFT, null));
        assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_NOT_FOUND);

        when(salesOrderDao.lock(ORDER_ID))
                .thenReturn(orderOf(ScmOrderSourceEnum.MALL, ScmOrderStatusEnum.CANCELLED, null));
        assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_NOT_FOUND);

        verifyNoInteractions(operationLogs);
    }

    @Test
    @DisplayName("草稿、待确认与已确认的商城单都可以被指派")
    void draftPendingAndConfirmedMallOrdersAreAllEligible() {
        for (ScmOrderStatusEnum status : List.of(ScmOrderStatusEnum.DRAFT, ScmOrderStatusEnum.PENDING,
                ScmOrderStatusEnum.CONFIRMED)) {
            SalesOrderEntity order = orderOf(ScmOrderSourceEnum.MALL, status, null);
            when(salesOrderDao.lock(ORDER_ID)).thenReturn(order);
            stubAssignableTarget();
            when(salesOrderDao.assignUnassignedMallOrder(ORDER_ID, 3, TARGET_SELLER, OPERATOR)).thenReturn(1);
            stubSnapshot(order);

            assertThat(service.assign(assignForm(ORDER_ID, 3, TARGET_SELLER), IDEMPOTENCY_KEY)).isNotNull();
        }
    }

    @Test
    @DisplayName("已有负责人时拒绝重复指派")
    void alreadyOwnedOrderIsRejected() {
        when(salesOrderDao.lock(ORDER_ID))
                .thenReturn(orderOf(ScmOrderSourceEnum.MALL, ScmOrderStatusEnum.PENDING, 9L));
        assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_ALREADY_ASSIGNED);
        verify(salesOrderDao, never()).assignUnassignedMallOrder(anyLong(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("请求版本落后于当前版本时拒绝，不触碰数据库写入")
    void staleVersionIsRejectedBeforeUpdate() {
        when(salesOrderDao.lock(ORDER_ID)).thenReturn(mallOrder(ORDER_ID, 5, null));
        assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), VERSION_CONFLICT);
        verify(salesOrderDao, never()).assignUnassignedMallOrder(anyLong(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("条件更新影响行数为零说明已被并发抢占，按版本冲突处理")
    void lostConditionalUpdateIsReportedAsVersionConflict() {
        SalesOrderEntity order = mallOrder(ORDER_ID, 3, null);
        when(salesOrderDao.lock(ORDER_ID)).thenReturn(order);
        stubAssignableTarget();
        when(salesOrderDao.assignUnassignedMallOrder(ORDER_ID, 3, TARGET_SELLER, OPERATOR)).thenReturn(0);

        assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), VERSION_CONFLICT);
        verifyNoInteractions(operationLogs);
        verify(idempotency, never()).complete(any(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("目标业务员不存在、已停用、已删除或没有订单查看权都拒绝")
    void invalidTargetSellerIsRejected() {
        when(salesOrderDao.lock(ORDER_ID)).thenReturn(mallOrder(ORDER_ID, 3, null));

        when(employeeDao.selectById(TARGET_SELLER)).thenReturn(null);
        assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_ASSIGN_TARGET_INVALID);

        when(employeeDao.selectById(TARGET_SELLER)).thenReturn(employee(TARGET_SELLER, "张三", false, true, false));
        assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_ASSIGN_TARGET_INVALID);

        when(employeeDao.selectById(TARGET_SELLER)).thenReturn(employee(TARGET_SELLER, "张三", false, false, true));
        assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_ASSIGN_TARGET_INVALID);

        when(employeeDao.selectById(TARGET_SELLER)).thenReturn(employee(TARGET_SELLER, "张三", false, false, false));
        when(loginManager.getUserPermission(TARGET_SELLER)).thenReturn(null);
        assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_ASSIGN_TARGET_INVALID);

        when(loginManager.getUserPermission(TARGET_SELLER)).thenReturn(permission());
        assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_ASSIGN_TARGET_INVALID);

        verify(salesOrderDao, never()).assignUnassignedMallOrder(anyLong(), any(), anyLong(), anyString());
        verifyNoInteractions(operationLogs);
    }

    @Test
    @DisplayName("超管身份的业务员无需订单查看权即可作为指派目标")
    void administratorTargetNeedsNoExtraPermission() {
        SalesOrderEntity order = mallOrder(ORDER_ID, 3, null);
        when(salesOrderDao.lock(ORDER_ID)).thenReturn(order);
        when(employeeDao.selectById(TARGET_SELLER)).thenReturn(employee(TARGET_SELLER, "张三", true, false, false));
        when(salesOrderDao.assignUnassignedMallOrder(ORDER_ID, 3, TARGET_SELLER, OPERATOR)).thenReturn(1);
        stubSnapshot(order);

        service.assign(assignForm(ORDER_ID, 3, TARGET_SELLER), IDEMPOTENCY_KEY);

        verify(loginManager, never()).getUserPermission(anyLong());
    }

    // ---------------------------------------------------------------- 成功路径

    @Test
    @DisplayName("指派成功后记录指派前后快照并回填幂等结果")
    void successfulAssignmentRecordsSnapshotAndCompletesIdempotency() {
        SalesOrderEntity order = mallOrder(ORDER_ID, 3, null);
        SalesOrderEntity reloaded = mallOrder(ORDER_ID, 4, TARGET_SELLER);
        SalesOrderVO before = new SalesOrderVO();
        before.setOrderId(ORDER_ID);
        SalesOrderVO after = new SalesOrderVO();
        after.setOrderId(ORDER_ID);
        when(salesOrderDao.lock(ORDER_ID)).thenReturn(order);
        stubAssignableTarget();
        when(salesOrderDao.assignUnassignedMallOrder(ORDER_ID, 3, TARGET_SELLER, OPERATOR)).thenReturn(1);
        when(salesOrderDao.selectById(ORDER_ID)).thenReturn(reloaded);
        when(queryService.order(order)).thenReturn(before);
        when(queryService.order(reloaded)).thenReturn(after);

        assertThat(service.assign(assignForm(ORDER_ID, 3, TARGET_SELLER), IDEMPOTENCY_KEY)).isSameAs(after);

        verify(operationLogs).record(ORDER_ID, ScmOrderOperationTypeEnum.ASSIGN, "指派业务员：张三", before, after);
        verify(idempotency).complete(any(), eq(ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name()),
                eq(ORDER_ID), eq(after));
    }

    // ---------------------------------------------------------------- 幂等

    @Test
    @DisplayName("重放命中幂等记录时直接返回原结果，不再锁定订单")
    void replayedAssignmentReturnsStoredResultWithoutLockingTheOrder() {
        SalesOrderVO stored = new SalesOrderVO();
        stored.setOrderId(ORDER_ID);
        var record = new ScmIdempotencyRecordEntity();
        record.setResultData(new LinkedHashMap<>(Map.of("value", Map.of())));
        var claim = new OrderIdempotencyService.Claim(record, true);
        when(idempotency.claim(anyString(), anyString(), any())).thenReturn(claim);
        when(idempotency.replay(claim, SalesOrderVO.class)).thenReturn(stored);

        assertThat(service.assign(assignForm(ORDER_ID, 3, TARGET_SELLER), IDEMPOTENCY_KEY)).isSameAs(stored);

        verifyNoInteractions(salesOrderDao, operationLogs);
        verify(idempotency, never()).complete(any(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("幂等指纹只认订单与目标业务员，版本前进后重试仍然命中")
    void fingerprintIgnoresVersionSoRetriesStillReplay() {
        when(salesOrderDao.lock(ORDER_ID)).thenReturn(null);
        var captor = ArgumentCaptor.forClass(Object.class);

        assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_NOT_FOUND);
        assertRejected(assignForm(ORDER_ID, 4, TARGET_SELLER), OrderErrorCode.ORDER_NOT_FOUND);
        verify(idempotency, times(2)).claim(anyString(), anyString(), captor.capture());

        List<Object> fingerprints = captor.getAllValues();
        assertThat(fingerprints).allSatisfy(fingerprint -> assertThat(fingerprintOf(fingerprint))
                .containsOnlyKeys("orderId", "sellerId"));
        assertThat(hasher.hash(fingerprintOf(fingerprints.get(1))))
                .isEqualTo(hasher.hash(fingerprintOf(fingerprints.get(0))));
    }

    @Test
    @DisplayName("换个订单或换个目标业务员都不能复用同一条幂等记录")
    void fingerprintSeparatesOrdersAndTargets() {
        when(salesOrderDao.lock(anyLong())).thenReturn(null);
        var captor = ArgumentCaptor.forClass(Object.class);

        assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_NOT_FOUND);
        assertRejected(assignForm(ORDER_ID, 3, 8L), OrderErrorCode.ORDER_NOT_FOUND);
        assertRejected(assignForm(11L, 3, TARGET_SELLER), OrderErrorCode.ORDER_NOT_FOUND);
        verify(idempotency, times(3)).claim(anyString(), anyString(), captor.capture());

        List<Object> fingerprints = captor.getAllValues();
        assertThat(hasher.hash(fingerprintOf(fingerprints.get(1))))
                .isNotEqualTo(hasher.hash(fingerprintOf(fingerprints.get(0))));
        assertThat(hasher.hash(fingerprintOf(fingerprints.get(2))))
                .isNotEqualTo(hasher.hash(fingerprintOf(fingerprints.get(0))));
    }

    // ---------------------------------------------------------------- 候选业务员

    @Test
    @DisplayName("候选业务员只保留有订单查看权或超管身份的人，按姓名升序、同名按工号升序、无姓名排最后")
    void assigneesKeepOnlyEmployeesAbleToOwnOrders() {
        when(employeeDao.selectList(any())).thenReturn(List.of(
                employee(7L, "陈一", false, false, false),
                employee(8L, "陈一", false, false, false),
                employee(5L, "阿一", true, false, false),
                employee(6L, "李四", false, false, false),
                employee(9L, null, true, false, false)));
        when(loginManager.getUserPermission(7L)).thenReturn(permission(OrderPermission.QUERY));
        when(loginManager.getUserPermission(8L)).thenReturn(permission(OrderPermission.QUERY));
        when(loginManager.getUserPermission(6L)).thenReturn(permission(OrderPermission.LOG_QUERY));

        List<OrderPoolAssigneeVO> result = service.assignees();

        assertThat(result).extracting(OrderPoolAssigneeVO::employeeId).containsExactly(5L, 7L, 8L, 9L);
        assertThat(result).extracting(OrderPoolAssigneeVO::actualName).containsExactly("阿一", "陈一", "陈一", null);
    }

    // ---------------------------------------------------------------- 夹具

    private void signIn(long employeeId, boolean administrator) {
        var user = new RequestEmployee();
        user.setEmployeeId(employeeId);
        user.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        user.setAdministratorFlag(administrator);
        SmartRequestUtil.setRequestUser(user);

        var context = mock(ScmDataScopeContext.class);
        when(context.getEmployeeId()).thenReturn(employeeId);
        when(dataScopeService.resolve()).thenReturn(context);
    }

    private void stubAssignableTarget() {
        when(employeeDao.selectById(TARGET_SELLER)).thenReturn(employee(TARGET_SELLER, "张三", false, false, false));
        when(loginManager.getUserPermission(TARGET_SELLER)).thenReturn(permission(OrderPermission.QUERY));
    }

    private void stubSnapshot(SalesOrderEntity order) {
        SalesOrderEntity reloaded = mallOrder(order.getId(), order.getVersion() + 1, TARGET_SELLER);
        SalesOrderVO before = new SalesOrderVO();
        before.setOrderId(order.getId());
        SalesOrderVO after = new SalesOrderVO();
        after.setOrderId(order.getId());
        when(salesOrderDao.selectById(order.getId())).thenReturn(reloaded);
        when(queryService.order(order)).thenReturn(before);
        when(queryService.order(reloaded)).thenReturn(after);
    }

    /** 幂等指纹是服务内部构造的映射，断言前统一规整成字符串键的映射。 */
    private static Map<String, Object> fingerprintOf(Object captured) {
        var normalized = new LinkedHashMap<String, Object>();
        ((Map<?, ?>) captured).forEach((key, value) -> normalized.put(String.valueOf(key), value));
        return normalized;
    }

    private static OrderPoolQueryForm queryForm() {
        var form = new OrderPoolQueryForm();
        form.setPageNum(1L);
        form.setPageSize(20L);
        return form;
    }

    private static OrderPoolAssignForm assignForm(long orderId, int version, long sellerId) {
        var form = new OrderPoolAssignForm();
        form.setOrderId(orderId);
        form.setVersion(version);
        form.setSellerId(sellerId);
        return form;
    }

    private static SalesOrderEntity mallOrder(long id, int version, Long sellerId) {
        return orderOf(ScmOrderSourceEnum.MALL, ScmOrderStatusEnum.PENDING, sellerId, id, version);
    }

    private static SalesOrderEntity orderOf(ScmOrderSourceEnum source, ScmOrderStatusEnum status, Long sellerId) {
        return orderOf(source, status, sellerId, ORDER_ID, 3);
    }

    private static SalesOrderEntity orderOf(ScmOrderSourceEnum source, ScmOrderStatusEnum status, Long sellerId,
            long id, int version) {
        var order = new SalesOrderEntity();
        order.setId(id);
        order.setOrderSource(source.name());
        order.setStatus(status.name());
        order.setSellerId(sellerId);
        order.setVersion(version);
        return order;
    }

    private static EmployeeEntity employee(long id, String actualName, boolean administrator, boolean disabled,
            boolean deleted) {
        var employee = new EmployeeEntity();
        employee.setEmployeeId(id);
        employee.setActualName(actualName);
        employee.setAdministratorFlag(administrator);
        employee.setDisabledFlag(disabled);
        employee.setDeletedFlag(deleted);
        return employee;
    }

    private static UserPermission permission(String... permissionCodes) {
        var permission = new UserPermission();
        permission.setPermissionList(List.of(permissionCodes));
        return permission;
    }

    private void assertRejected(OrderPoolAssignForm form, ScmErrorCode expected) {
        assertThatThrownBy(() -> service.assign(form, IDEMPOTENCY_KEY))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
