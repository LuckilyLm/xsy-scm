package com.xsy.scm.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.mall.account.domain.form.MallAccountCreateForm;
import com.xsy.scm.mall.account.domain.form.MallLoginForm;
import com.xsy.scm.mall.account.domain.form.MallPasswordChangeForm;
import com.xsy.scm.mall.account.domain.vo.MallAccountVO;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.account.service.MallAccountService;
import com.xsy.scm.mall.account.service.MallAuthService;
import com.xsy.scm.mall.address.domain.form.MallAddressSaveForm;
import com.xsy.scm.mall.address.service.MallAddressService;
import com.xsy.scm.mall.cart.domain.form.MallCartAddForm;
import com.xsy.scm.mall.cart.service.MallCartService;
import com.xsy.scm.mall.order.domain.form.MallCheckoutItemForm;
import com.xsy.scm.mall.order.domain.form.MallCheckoutPreviewForm;
import com.xsy.scm.mall.order.domain.form.MallOrderSubmitForm;
import com.xsy.scm.mall.order.domain.vo.MallCheckoutPreviewVO;
import com.xsy.scm.mall.order.service.MallCheckoutService;
import com.xsy.scm.order.domain.form.OrderPoolAssignForm;
import com.xsy.scm.order.domain.form.OrderPoolClaimForm;
import com.xsy.scm.order.service.OrderPoolService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 未分配订单池被真并发抢单时的收敛（PG IT，无外层事务）。
 *
 * <p>两个人各自发起一次领取或指派，幂等键只能防「同一个请求重发」，防不了「两个人同时点」。
 * 财务上「一张订单只能有一个负责人」必须由库来保证 —— 这里唯一的仲裁点是
 * {@code assignUnassignedMallOrder} 的条件更新（{@code version = ? AND seller_id IS NULL}）。
 *
 * <p>断言只看最终事实，不写死哪个线程先成功（那是在测调度器）：恰好一次指派、恰好一条
 * ASSIGN 审计、版本只前进一次，失败侧必须是可读的业务码（40973 已被指派 / 40921 版本冲突），
 * 不能是死锁、超时或裸唯一键异常。
 *
 * <p>本类无外层事务，因此夹具与写入都是真提交：线程各自开连接才能形成真正的竞争。
 * 被抢占的行来自真实的商城下单链路（客户未分配业务员），不直插订单表 ——
 * 直插会绕过订单来源与状态的不变量，让「未分配商城单」这个前提失去意义。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("未分配订单池并发抢单的收敛（PG IT，无外层事务）")
class OrderPoolAssignmentConcurrencyPgIT extends ScmW5PgITBase {

    private static final String TEMPORARY_PASSWORD = "Mall@123456";
    private static final String NEW_PASSWORD = "Mall@654321";

    /** 输家可以拿到的两个业务码：抢输给「已有负责人」或「版本已被改」。 */
    private static final int ALREADY_ASSIGNED = 40973;
    private static final int VERSION_CONFLICT = 40921;

    @Autowired
    private OrderPoolService orderPoolService;

    @Autowired
    private MallAccountService mallAccountService;

    @Autowired
    private MallAuthService mallAuthService;

    @Autowired
    private MallCartService mallCartService;

    @Autowired
    private MallAddressService mallAddressService;

    @Autowired
    private MallCheckoutService mallCheckoutService;

    private int customerSequence;

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
    }

    // ------------------------------------------------------------------
    // 并发领取：两名主管同时点「领取」
    // ------------------------------------------------------------------

    @Test
    @DisplayName("两名主管同时领取同一张订单：恰好一次指派、一条审计，失败侧是业务码而不是死锁")
    void concurrentClaimsConvergeToASingleAssignment() throws Exception {
        PoolOrder order = unassignedMallOrder("claim");

        var firstFailure = new AtomicReference<Throwable>();
        var secondFailure = new AtomicReference<Throwable>();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);

        run("claim-a", () -> orderPoolService.claim(claimForm(order), key("claim-a")),
                firstFailure, ready, start, done);
        run("claim-b", () -> orderPoolService.claim(claimForm(order), key("claim-b")),
                secondFailure, ready, start, done);

        awaitConvergence(ready, start, done);

        assertExactlyOneWinner(firstFailure, secondFailure);
        assertSingleAssignment(order, null);
    }

    // ------------------------------------------------------------------
    // 并发指派：两名主管各指给不同的业务员
    // ------------------------------------------------------------------

    @Test
    @DisplayName("两名主管同时把同一张订单指给不同业务员：只有一个人被写进负责人，审计不重复")
    void concurrentAssignmentsToDifferentSellersConvergeToOneOwner() throws Exception {
        PoolOrder order = unassignedMallOrder("assign");
        Long firstTarget = newAssignableEmployee("t1");
        Long secondTarget = newAssignableEmployee("t2");

        var firstFailure = new AtomicReference<Throwable>();
        var secondFailure = new AtomicReference<Throwable>();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);

        run("assign-a", () -> orderPoolService.assign(assignForm(order, firstTarget), key("assign-a")),
                firstFailure, ready, start, done);
        run("assign-b", () -> orderPoolService.assign(assignForm(order, secondTarget), key("assign-b")),
                secondFailure, ready, start, done);

        awaitConvergence(ready, start, done);

        assertExactlyOneWinner(firstFailure, secondFailure);
        assertSingleAssignment(order, List.of(firstTarget, secondTarget));
    }

    // ------------------------------------------------------------------
    // 同键并发重发：同一个人手抖点了两次
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同一幂等键并发重发：两侧都拿到同一张单，库里只有一次指派和一条审计")
    void concurrentRetryWithTheSameIdempotencyKeyReplaysInsteadOfAssigningTwice() throws Exception {
        PoolOrder order = unassignedMallOrder("retry");
        String sharedKey = key("shared");

        var firstFailure = new AtomicReference<Throwable>();
        var secondFailure = new AtomicReference<Throwable>();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);

        run("retry-a", () -> orderPoolService.claim(claimForm(order), sharedKey),
                firstFailure, ready, start, done);
        run("retry-b", () -> orderPoolService.claim(claimForm(order), sharedKey),
                secondFailure, ready, start, done);

        awaitConvergence(ready, start, done);

        // 同键重发是「同一个请求」，两侧都不该失败：一侧真正执行，另一侧重放已存结果
        assertThat(firstFailure.get()).as("同键并发重发不得失败").isNull();
        assertThat(secondFailure.get()).as("同键并发重发不得失败").isNull();
        assertSingleAssignment(order, null);
    }

    // ------------------------------------------------------------------
    // 断言辅助
    // ------------------------------------------------------------------

    private void awaitConvergence(CountDownLatch ready, CountDownLatch start, CountDownLatch done) throws Exception {
        assertThat(ready.await(30, TimeUnit.SECONDS)).as("两个线程都要到达起跑线").isTrue();
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS))
                .as("两个事务必须在 60 秒内结束（否则疑似死锁或长等待）").isTrue();
    }

    private void assertExactlyOneWinner(AtomicReference<Throwable> first, AtomicReference<Throwable> second) {
        boolean firstFailed = first.get() != null;
        boolean secondFailed = second.get() != null;
        assertThat(firstFailed ^ secondFailed)
                .as("一侧成功、一侧被条件更新拒绝，不得双双成功或双双失败")
                .isTrue();

        Throwable loser = firstFailed ? first.get() : second.get();
        assertThat(loser)
                .as("失败必须是可读的业务异常，不能把死锁 / 超时 / 裸约束异常抛到接口")
                .isInstanceOf(ScmBusinessException.class)
                .isNotInstanceOf(DataAccessException.class);
        assertThat(((ScmBusinessException) loser).getErrorCode().getCode())
                .as("输家只能是「已被指派」或「版本冲突」")
                .isIn(ALREADY_ASSIGNED, VERSION_CONFLICT);
    }

    /**
     * 最终事实：负责人唯一、版本只前进一次、指派审计只有一条。
     *
     * @param expectedOwner 期望的负责人；传 {@code null} 表示只断言「恰好有一个人」
     */
    private void assertSingleAssignment(PoolOrder order, List<Long> expectedOwner) {
        Long owner = jdbc.queryForObject("SELECT seller_id FROM xsy_sales_order WHERE id = ?",
                Long.class, order.orderId());
        assertThat(owner).as("订单最终必须恰好有一个负责人").isNotNull();
        if (expectedOwner != null) {
            assertThat(owner).as("负责人必须是两个候选之一").isIn(expectedOwner);
        }
        assertThat(jdbc.queryForObject("SELECT version FROM xsy_sales_order WHERE id = ?",
                Integer.class, order.orderId()))
                .as("条件更新只成功一次，版本就只前进一次").isEqualTo(order.version() + 1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM xsy_order_operation_log WHERE order_id = ? AND operation_type = 'ASSIGN'",
                Integer.class, order.orderId()))
                .as("指派审计与业务变更同事务，因此只能有一条").isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private record PoolOrder(Long orderId, Integer version) {
    }

    /**
     * 走真实商城链路造一张「未分配」的商城订单：客户没有业务员，订单 seller_id 跟着为空，
     * 状态停在 PENDING（非标品要等后台录实重才确认），正是订单池的准入形态。
     */
    private PoolOrder unassignedMallOrder(String tag) {
        Long skuId = newOnShelfSku("pool-" + tag);
        Long customerId = newCustomerWithoutSeller();
        MallAccount account = openMallAccount(customerId);
        RequestMallCustomer customer = signIn(account);
        Long addressId = mallAddressService.create(customer, mallAddress()).getFirst().getAddressId();

        SmartRequestUtil.setRequestUser(customer);
        mallCartService.add(customer, cartAdd(skuId, "1.0000"));
        MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
        var submitted = mallCheckoutService.submit(customer,
                submitForm(skuId, addressId, preview.getPriceFingerprint()), prefix + ":mall:" + tag);

        Long sellerId = jdbc.queryForObject("SELECT seller_id FROM xsy_sales_order WHERE id = ?",
                Long.class, submitted.getOrderId());
        assertThat(sellerId).as("夹具前提：客户未分配业务员，订单也必须未分配").isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM xsy_sales_order WHERE id = ?",
                String.class, submitted.getOrderId()))
                .as("夹具前提：非标品商城单停在待确认").isEqualTo("PENDING");

        Integer version = jdbc.queryForObject("SELECT version FROM xsy_sales_order WHERE id = ?",
                Integer.class, submitted.getOrderId());
        return new PoolOrder(submitted.getOrderId(), version);
    }

    /**
     * 造一个未分配业务员的客户。
     *
     * <p>借 {@code CUSTOMER_ASSIGN_PERM}：{@code CustomerService.resolveSellerOnCreate} 在没有分配权时
     * 会把业务员落成「当前登录人」，造不出未分配客户。分配权是真实权限，这里只是以具备该权限的身份造数。
     */
    private Long newCustomerWithoutSeller() {
        CustomerAddForm form = new CustomerAddForm();
        form.setName(prefix + "-P" + (++customerSequence) + "客户");
        form.setCustomerTypeId(customerTypeId("ENTERPRISE"));
        form.setSettleMode("INDEPENDENT");
        form.setCodAllowed(true);
        form.setSellerId(null);
        Long customerId;
        try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
            when(StpUtil.hasPermission(ScmDataScopeService.CUSTOMER_ASSIGN_PERM)).thenReturn(true);
            customerId = customerService.add(form);
        }
        CustomerStatusForm status = new CustomerStatusForm();
        status.setCustomerId(customerId);
        status.setVersion(0);
        status.setStatus("COOPERATING");
        customerService.updateStatus(status);
        return customerId;
    }

    /**
     * 造一个可作为指派目标的员工。
     *
     * <p>{@code administratorFlag = TRUE} 让 {@code OrderPoolService.canReceiveOrder} 直接放行：
     * 本用例的被测对象是并发收敛，不是「目标业务员有没有订单查看权」（那条判定由单测覆盖）。
     */
    private Long newAssignableEmployee(String suffix) {
        String login = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, disabled_flag, deleted_flag)"
                        + " VALUES (?, ?, ?, ?, 1, TRUE, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), login, "$argon2id$it-placeholder", "指派目标" + suffix);
        return jdbc.queryForObject("SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, login);
    }

    private MallAccount openMallAccount(Long customerId) {
        String phone = newPhone();
        MallAccountCreateForm form = new MallAccountCreateForm();
        form.setCustomerId(customerId);
        form.setLoginPhone(phone);
        form.setTemporaryPassword(TEMPORARY_PASSWORD);
        MallAccountVO account = mallAccountService.create(form);
        return new MallAccount(account.getAccountId(), phone);
    }

    /**
     * 走真实登录动作并完成首次改密，返回该客户的请求身份。
     *
     * <p>改密不是可省略的仪式：商城接口有「临时密码未改」的前置守卫，跳过它就等于跳过了那道守卫。
     */
    private RequestMallCustomer signIn(MallAccount account) {
        MallLoginForm login = new MallLoginForm();
        login.setPhone(account.phone());
        login.setPassword(TEMPORARY_PASSWORD);
        try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
            mallAuthService.login(login, "127.0.0.1");
            MallPasswordChangeForm change = new MallPasswordChangeForm();
            change.setOldPassword(TEMPORARY_PASSWORD);
            change.setNewPassword(NEW_PASSWORD);
            mallAuthService.changePassword(requestCustomer(account, 0), change);
            login.setPassword(NEW_PASSWORD);
            mallAuthService.login(login, "127.0.0.1");
        }
        return requestCustomer(account, 1);
    }

    private RequestMallCustomer requestCustomer(MallAccount account, int version) {
        RequestMallCustomer customer = mallAuthService.requestCustomer(
                MallAuthService.loginId(account.accountId(), version), new MockHttpServletRequest());
        assertThat(customer).as("登录后必须能解析出商城客户身份").isNotNull();
        return customer;
    }

    /**
     * 以「全量范围的操作者」身份执行并发动作。
     *
     * <p>线程内必须重新设置请求身份：{@code SmartRequestUtil} 是 ThreadLocal，
     * 而订单池的权限闸门与操作人都从它取。
     */
    private void run(String name, Runnable body, AtomicReference<Throwable> failure,
                     CountDownLatch ready, CountDownLatch start, CountDownLatch done) {
        new Thread(() -> {
            asSupervisor(name);
            try {
                ready.countDown();
                start.await();
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                SmartRequestUtil.remove();
                done.countDown();
            }
        }, name).start();
    }

    private void asSupervisor(String name) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName(name);
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

    private OrderPoolClaimForm claimForm(PoolOrder order) {
        OrderPoolClaimForm form = new OrderPoolClaimForm();
        form.setOrderId(order.orderId());
        form.setVersion(order.version());
        return form;
    }

    private OrderPoolAssignForm assignForm(PoolOrder order, Long sellerId) {
        OrderPoolAssignForm form = new OrderPoolAssignForm();
        form.setOrderId(order.orderId());
        form.setVersion(order.version());
        form.setSellerId(sellerId);
        return form;
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    private static MallCartAddForm cartAdd(Long skuId, String quantity) {
        MallCartAddForm form = new MallCartAddForm();
        form.setSkuId(skuId);
        form.setQuantity(quantity);
        return form;
    }

    private static MallAddressSaveForm mallAddress() {
        MallAddressSaveForm form = new MallAddressSaveForm();
        form.setReceiverName("订单池收货人");
        form.setReceiverPhone("13800000000");
        form.setProvinceName("上海市");
        form.setCityName("上海市");
        form.setDistrictName("浦东新区");
        form.setDetailAddress("IT 测试路 2 号");
        form.setIsDefault(true);
        return form;
    }

    private static MallCheckoutPreviewForm previewForm(Long skuId, Long addressId) {
        MallCheckoutPreviewForm form = new MallCheckoutPreviewForm();
        form.setItems(List.of(checkoutItem(skuId, "1.0000")));
        form.setAddressId(addressId);
        return form;
    }

    private static MallOrderSubmitForm submitForm(Long skuId, Long addressId, String priceFingerprint) {
        MallOrderSubmitForm form = new MallOrderSubmitForm();
        form.setItems(List.of(checkoutItem(skuId, "1.0000")));
        form.setAddressId(addressId);
        form.setPriceFingerprint(priceFingerprint);
        form.setRemark("订单池并发 IT");
        form.setSettlementMethod("COD");
        return form;
    }

    private static MallCheckoutItemForm checkoutItem(Long skuId, String quantity) {
        MallCheckoutItemForm item = new MallCheckoutItemForm();
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        return item;
    }

    private static String newPhone() {
        long suffix = Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000_000L);
        return "13" + (100_000_000L + suffix);
    }

    private record MallAccount(Long accountId, String phone) {
    }
}
