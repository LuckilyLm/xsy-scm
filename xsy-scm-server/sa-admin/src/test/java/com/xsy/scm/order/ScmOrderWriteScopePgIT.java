package com.xsy.scm.order;

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

import cn.dev33.satoken.stp.StpUtil;

import com.xsy.scm.common.ScmW3PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.order.domain.form.OrderActualQuantityForm;
import com.xsy.scm.order.domain.form.OrderAddressForm;
import com.xsy.scm.order.domain.form.OrderCancelForm;
import com.xsy.scm.order.domain.form.OrderReturnAddForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveItemForm;
import com.xsy.scm.order.domain.form.OrderReturnDecisionForm;
import com.xsy.scm.order.domain.form.OrderReturnItemForm;
import com.xsy.scm.order.domain.form.OrderVersionForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderItemForm;
import com.xsy.scm.order.domain.form.SalesOrderUpdateForm;
import com.xsy.scm.order.service.OrderReturnService;
import com.xsy.scm.order.service.SalesOrderService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.base.common.util.SmartRequestUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * SEC-01 / SEC-02 回归：订单族与退货族的<b>写命令入口</b>必须与读接口同口径地判归属。
 *
 * <p>被测缺陷是一条「读收窄、写不设防」的错配：查询与详情早就按 {@code orderSellerScope}
 * 只给出本人负责的行，但 {@code update / submit / actualQuantity / cancel / delete /
 * reserveStock} 与退货的 {@code create / approve / reject / cancel} 只判「单据存在」，
 * 于是持有功能点权限的普通人员<b>只要猜到 id</b> 就能改别人的单、动别人的数量、占别人的库存、
 * 替别人批退货退款 —— 行级范围沦为「藏起来」。
 *
 * <p><b>为什么必须真库 + 真服务</b>：缝隙不在单个方法里，而在「读用的范围」与「写用的范围」
 * 两条路径之间。用 Mockito 把 DAO 打桩会把这个差异擦掉，只能跑真实 PG：范围解析读登录员工与
 * {@code StpUtil} 权限，订单行与退货行必须真存在，锁序与版本比对的先后顺序也只有在真事务里才有意义。
 *
 * <p><b>身份与权限怎么造</b>：沿用 {@code ScmOrderCustomerDataScopePgIT} 的既有做法 ——
 * {@code SmartRequestUtil} 覆盖登录员工，{@code mockStatic(StpUtil.class)} 显式给功能权限；
 * 未点名的权限码取 Mockito 默认 false，正好等于生产上的失败关闭。测试员工一律
 * {@code administrator_flag=false}，超管通过不构成权限证据。
 *
 * <p><b>幂等重放也要过范围</b>：{@code ScmIdempotencyService} 的 scope 形如
 * {@code operator + ":" + scope}，操作员由 {@code ScmOperator.current()} 取
 * {@code userType:userId}，因此 A 与 B 的键在存储层就是两行 —— 跨人重放<b>结构上不可能</b>。
 * 但「同一个人换了负责范围后再重放同一个键」会直接命中 {@code replay} 分支并跳过写命令里的门禁，
 * 所以每个重放分支都必须自己再判一次范围。这里逐个用例证明这一点。
 */
@DisplayName("SEC-01/SEC-02 订单与退货写入口的归属门禁（PG IT）")
class ScmOrderWriteScopePgIT extends ScmW3PgITBase {

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private OrderReturnService orderReturnService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private org.apache.ibatis.session.SqlSession session;

    /** 两个普通销售：各自负责自己的客户与订单，功能权限相同、数据范围不同。 */
    private Long sellerA;
    private Long sellerB;

    /** 「未分配」订单（{@code seller_id IS NULL}）不对任何普通销售放行。 */
    @BeforeEach
    void setUpEmployees() {
        sellerA = newEmployee("A");
        sellerB = newEmployee("B");
    }

    // ==================== SEC-01：订单写命令 ====================

    @Test
    @DisplayName("订单编辑：改别人的草稿 30005，改自己的照常成功")
    void updateRejectsForeignOrderButAllowsOwn() {
        Long customerOfB = createTradableCustomer("U1", sellerB);
        Long sku = newOnShelfSku("U1");
        Long orderOfB = draftOrder(customerOfB, sku, sellerB, "u1");
        Integer before = versionOf(orderOfB);

        assertNoPermission(() -> as(sellerA, Set.of(), () -> salesOrderService.update(
                updateForm(orderOfB, versionOf(orderOfB), customerOfB, sku))));

        // 越权被拒不是「改了一半」：备注与版本都必须是原样（update 会 bump 版本）
        assertThat(versionOf(orderOfB)).isEqualTo(before);
        assertThat(remarkOf(orderOfB)).isNotEqualTo("写范围测试-编辑");
        assertThat(as(sellerB, Set.of(), () -> salesOrderService.update(
                updateForm(orderOfB, versionOf(orderOfB), customerOfB, sku)).getOrderId())).isEqualTo(orderOfB);
        assertThat(remarkOf(orderOfB)).isEqualTo("写范围测试-编辑");
    }

    @Test
    @DisplayName("订单提交：替别人提交 30005；自己的单提交成功进入 PENDING")
    void submitRejectsForeignOrderButAllowsOwn() {
        Long customerOfB = createTradableCustomer("S1", sellerB);
        Long sku = newOnShelfSku("S1");
        Long orderOfB = draftOrder(customerOfB, sku, sellerB, "s1");

        assertNoPermission(() -> as(sellerA, Set.of(),
                () -> salesOrderService.submit(versionForm(orderOfB, versionOf(orderOfB)), prefix + "-s1a")));
        // 越权不是「改了一半」：单还是草稿，版本没被 bump
        assertThat(statusOf(orderOfB)).isEqualTo("DRAFT");

        assertThat(as(sellerB, Set.of(),
                () -> salesOrderService.submit(versionForm(orderOfB, versionOf(orderOfB)), prefix + "-s1b")
                        .getStatus()))
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("订单提交幂等重放：同一个人换了范围后重放旧键同样 30005")
    void submitReplayDoesNotBypassScope() {
        Long customerOfB = createTradableCustomer("S2", sellerB);
        Long sku = newOnShelfSku("S2");
        Long orderOfB = draftOrder(customerOfB, sku, sellerB, "s2");
        String key = prefix + "-s2";

        // B 先把单提交掉，键落库并带上结果。重放要命中同一行，请求体必须逐字节一致 → 复用同一个 form
        OrderVersionForm form = versionForm(orderOfB, versionOf(orderOfB));
        as(sellerB, Set.of(), () -> salesOrderService.submit(form, key));

        // A 用同一张单、同一个键重放：scope 含 operator，A 与 B 是两行 → 不命中重放，
        // 走写命令后在订单锁之后被范围门禁拦下。
        assertNoPermission(() -> as(sellerA, Set.of(), () -> salesOrderService.submit(form, key)));

        // B 自己重放同一个键、同样的请求体：命中 replay 分支，replay 前先 detail() → 范围成立，正常返回
        assertThat(as(sellerB, Set.of(), () -> salesOrderService.submit(form, key).getOrderId()))
                .isEqualTo(orderOfB);
    }

    @Test
    @DisplayName("实收数量：改别人单的数量 30005；自己的单照常")
    void actualQuantityRejectsForeignOrderButAllowsOwn() {
        Long customerOfB = createTradableCustomer("Q1", sellerB);
        Long sku = newOnShelfSku("Q1");
        Long orderOfB = draftOrder(customerOfB, sku, sellerB, "q1");
        Long itemId = firstItemId(orderOfB);
        // 实收数量只在 PENDING 上允许，先由负责人自己提交；本用例要验的是那道归属门禁
        as(sellerB, Set.of(), () -> salesOrderService.submit(
                versionForm(orderOfB, versionOf(orderOfB)), prefix + "-q1sub"));
        assertThat(statusOf(orderOfB)).isEqualTo("PENDING");

        assertNoPermission(() -> as(sellerA, Set.of(), () -> salesOrderService.actualQuantity(
                actualForm(orderOfB, itemId, versionOf(orderOfB), "1.0000"), prefix + "-q1a")));
        // 越权被拒后实数量仍为空
        assertThat(actualQuantityOf(itemId)).isNull();

        assertThat(as(sellerB, Set.of(), () -> salesOrderService.actualQuantity(
                actualForm(orderOfB, itemId, versionOf(orderOfB), "1.0000"), prefix + "-q1b").getOrderId()))
                .isEqualTo(orderOfB);
    }

    @Test
    @DisplayName("订单取消：取消别人的单 30005，且状态不变；自己的单照常取消")
    void cancelRejectsForeignOrderButAllowsOwn() {
        Long customerOfB = createTradableCustomer("C1", sellerB);
        Long sku = newOnShelfSku("C1");
        Long orderOfB = draftOrder(customerOfB, sku, sellerB, "c1");

        assertNoPermission(() -> as(sellerA, Set.of(), () -> salesOrderService.cancel(
                cancelForm(orderOfB, versionOf(orderOfB)), prefix + "-c1a")));
        assertThat(as(sellerB, Set.of(), () -> statusOf(orderOfB))).isEqualTo("DRAFT");

        assertThat(as(sellerB, Set.of(), () -> salesOrderService.cancel(
                cancelForm(orderOfB, versionOf(orderOfB)), prefix + "-c1b").getStatus()))
                .isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("订单删除：删别人的草稿 30005，单据仍在；自己的草稿照常删")
    void deleteRejectsForeignOrderButAllowsOwn() {
        Long customerOfB = createTradableCustomer("D1", sellerB);
        Long sku = newOnShelfSku("D1");
        Long orderOfB = draftOrder(customerOfB, sku, sellerB, "d1");

        assertNoPermission(() -> as(sellerA, Set.<String>of(), () -> {
            salesOrderService.delete(versionForm(orderOfB, versionOf(orderOfB)));
            return null;
        }));
        assertThat(deletedOf(orderOfB)).as("越权删除不得真的落库").isFalse();
        assertThat(statusOf(orderOfB)).isEqualTo("DRAFT");

        // 负责人自己删：软删标记必须落地
        as(sellerB, Set.of(), () -> {
            salesOrderService.delete(versionForm(orderOfB, versionOf(orderOfB)));
            return null;
        });
        assertThat(deletedOf(orderOfB)).isTrue();
    }

    @Test
    @DisplayName("预留库存：替别人的已确认单预留 30005，不留下任何预留行")
    void reserveStockRejectsForeignOrder() {
        Long customerOfB = createTradableCustomer("R1", sellerB);
        Long sku = newOnShelfSku("R1");
        Long orderOfB = draftOrder(customerOfB, sku, sellerB, "r1");
        // 用负责人自己的身份把单推到 CONFIRMED：本用例要验的是预留那道门禁，不是状态机
        confirmOwn(orderOfB);
        assertThat(as(sellerB, Set.of(), () -> statusOf(orderOfB))).isEqualTo("CONFIRMED");

        assertNoPermission(() -> as(sellerA, Set.of(), () -> {
            salesOrderService.reserveStock(orderOfB);
            return null;
        }));

        // 越权被拒必须在业务数据上留痕为零：不能出现「拒了但已经占了货」
        assertThat(reservationRowsFor(orderOfB)).isZero();
    }

    // ==================== SEC-02：退货写命令 ====================

    @Test
    @DisplayName("退货建单：替别人的订单建退货 30005，退货行不落库")
    void returnCreateRejectsForeignOrder() {
        Long customerOfB = createTradableCustomer("T1", sellerB);
        Long sku = newOnShelfSku("T1");
        Long orderOfB = confirmedOrder(customerOfB, sku, sellerB, "t1");
        Long itemId = firstItemId(orderOfB);

        assertNoPermission(() -> as(sellerA, Set.of(), () -> orderReturnService.create(
                returnAddForm(orderOfB, itemId, "1.0000"), prefix + "-t1a")));

        assertThat(returnRowsFor(orderOfB)).isZero();

        assertThat(as(sellerB, Set.of(), () -> orderReturnService.create(
                returnAddForm(orderOfB, firstItemId(orderOfB), "1.0000"), prefix + "-t1b").getOrderId()))
                .isEqualTo(orderOfB);
    }

    @Test
    @DisplayName("退货建单幂等重放：A 拿着 B 的订单号重放旧键仍然 30005")
    void returnCreateReplayDoesNotBypassScope() {
        Long customerOfB = createTradableCustomer("T2", sellerB);
        Long sku = newOnShelfSku("T2");
        Long orderOfB = confirmedOrder(customerOfB, sku, sellerB, "t2");
        String key = prefix + "-t2";
        // 重放要命中同一行，请求体必须逐字节一致 → 复用同一个 form
        OrderReturnAddForm form = returnAddForm(orderOfB, firstItemId(orderOfB), "1.0000");

        Long returnId = as(sellerB, Set.of(), () -> orderReturnService.create(form, key).getReturnId());
        assertThat(returnId).isNotNull();

        // A 用自己的键 → 不是重放，走写命令，父订单范围门禁拦下
        assertNoPermission(() -> as(sellerA, Set.of(), () -> orderReturnService.create(form, prefix + "-t2-a")));

        // B 重放自己的键、同样的请求体：replay 分支里 requireParentOrderVisible 仍然成立 → 正常返回
        assertThat(as(sellerB, Set.of(), () -> orderReturnService.create(form, key).getReturnId()))
                .isEqualTo(returnId);
    }

    @Test
    @DisplayName("退货审批：批别人的退货单 30005，状态仍 PENDING；自己的单照常批")
    void returnApproveRejectsForeignReturn() {
        Long customerOfB = createTradableCustomer("T3", sellerB);
        Long sku = newOnShelfSku("T3");
        Long orderOfB = confirmedOrder(customerOfB, sku, sellerB, "t3");
        Long itemId = firstItemId(orderOfB);
        Long returnId = as(sellerB, Set.of(), () -> orderReturnService.create(
                returnAddForm(orderOfB, itemId, "1.0000"), prefix + "-t3").getReturnId());

        assertNoPermission(() -> as(sellerA, Set.of(), () -> orderReturnService.approve(
                approveForm(returnId, returnVersionOf(returnId), itemId, "1.0000"), prefix + "-t3a")));
        assertThat(returnStatusOf(returnId)).isEqualTo("PENDING");

        assertThat(as(sellerB, Set.of(), () -> orderReturnService.approve(
                approveForm(returnId, returnVersionOf(returnId), itemId, "1.0000"), prefix + "-t3b").getStatus()))
                .isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("退货驳回：驳别人的退货单 30005；自己的单照常驳回")
    void returnRejectRejectsForeignReturn() {
        Long customerOfB = createTradableCustomer("T4", sellerB);
        Long sku = newOnShelfSku("T4");
        Long orderOfB = confirmedOrder(customerOfB, sku, sellerB, "t4");
        Long returnId = as(sellerB, Set.of(), () -> orderReturnService.create(
                returnAddForm(orderOfB, firstItemId(orderOfB), "1.0000"), prefix + "-t4").getReturnId());

        assertNoPermission(() -> as(sellerA, Set.of(), () -> orderReturnService.reject(
                decisionForm(returnId, returnVersionOf(returnId), "越权驳回"), prefix + "-t4a")));
        assertThat(returnStatusOf(returnId)).isEqualTo("PENDING");

        assertThat(as(sellerB, Set.of(), () -> orderReturnService.reject(
                decisionForm(returnId, returnVersionOf(returnId), "负责人驳回"), prefix + "-t4b").getStatus()))
                .isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("退货取消：取消别人的退货单 30005；自己的单照常取消")
    void returnCancelRejectsForeignReturn() {
        Long customerOfB = createTradableCustomer("T5", sellerB);
        Long sku = newOnShelfSku("T5");
        Long orderOfB = confirmedOrder(customerOfB, sku, sellerB, "t5");
        Long returnId = as(sellerB, Set.of(), () -> orderReturnService.create(
                returnAddForm(orderOfB, firstItemId(orderOfB), "1.0000"), prefix + "-t5").getReturnId());

        assertNoPermission(() -> as(sellerA, Set.of(), () -> orderReturnService.cancel(
                decisionForm(returnId, returnVersionOf(returnId), "越权取消"), prefix + "-t5a")));
        assertThat(returnStatusOf(returnId)).isEqualTo("PENDING");

        assertThat(as(sellerB, Set.of(), () -> orderReturnService.cancel(
                decisionForm(returnId, returnVersionOf(returnId), "负责人取消"), prefix + "-t5b").getStatus()))
                .isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("审批幂等重放：同一个人换了范围后重放旧键仍 30005（replay 前先 detail）")
    void returnApproveReplayDoesNotBypassScope() {
        Long customerOfB = createTradableCustomer("T6", sellerB);
        Long sku = newOnShelfSku("T6");
        Long orderOfB = confirmedOrder(customerOfB, sku, sellerB, "t6");
        Long itemId = firstItemId(orderOfB);
        Long returnId = as(sellerB, Set.of(), () -> orderReturnService.create(
                returnAddForm(orderOfB, itemId, "1.0000"), prefix + "-t6").getReturnId());
        String key = prefix + "-t6-approve";
        // 复用同一 form：重放要命中同一行，请求体必须逐字节一致
        OrderReturnApproveForm form = approveForm(returnId, returnVersionOf(returnId), itemId, "1.0000");

        as(sellerB, Set.of(), () -> orderReturnService.approve(form, key));

        // A 用同一个键重放：scope 含 operator，A 的行与 B 的行不同 → 不命中重放，
        // 落到写命令后由 lock() 里的父订单范围门禁拦下（否则猜个 returnId 就能重放别人的审批结果）
        assertNoPermission(() -> as(sellerA, Set.of(), () -> orderReturnService.approve(form, key)));

        // B 自己重放：命中 replay，但 replay 前 detail() 会重新判范围 → 正常返回
        assertThat(as(sellerB, Set.of(), () -> orderReturnService.approve(form, key).getReturnId()))
                .isEqualTo(returnId);
    }

    @Test
    @DisplayName("未分配订单（seller_id IS NULL）不对普通销售放行，全量范围者可写")
    void unassignedOrderIsNotWritableByPlainSeller() {
        Long customerOfA = createTradableCustomer("N1", sellerA);
        String orderNo = prefix + "-N1";
        // 直接插一张 seller_id 为 null 的草稿：模拟「归属被收回」的历史单。
        // 用 delete 作为探针：它只需要订单行本身，不依赖地址快照或订单行，
        // 能把「范围判定」这一件事单独隔离出来。
        jdbc.update("INSERT INTO sales_order (order_no, customer_id, customer_code_snapshot,"
                        + " customer_name_snapshot, settlement_customer_id, settlement_customer_name_snapshot,"
                        + " order_source, status, ordered_total_amount, settlement_total_amount,"
                        + " settle_mode_snapshot, seller_id)"
                        + " VALUES (?, ?, 'CODE-N1', '范围测试客户', ?, '范围测试客户', 'ADMIN', 'DRAFT', 0, 0,"
                        + " 'INDEPENDENT', NULL)",
                orderNo, customerOfA, customerOfA);
        Long unassigned = jdbc.queryForObject("SELECT id FROM sales_order WHERE order_no = ?", Long.class, orderNo);

        // A 是「原本可能负责过这户客户」的普通销售：范围谓词只认 seller_id IN (本人)，null 不在其中
        assertNoPermission(() -> as(sellerA, Set.of(), () -> {
            salesOrderService.delete(versionForm(unassigned, versionOf(unassigned)));
            return null;
        }));
        assertThat(deletedOf(unassigned)).isFalse();

        // 全量范围：all() 对任何 sellerId 都放行（含 null），删除正常落地
        as(sellerA, Set.of(ScmDataScopeService.ORDER_ALL_PERM), () -> {
            salesOrderService.delete(versionForm(unassigned, versionOf(unassigned)));
            return null;
        });
        assertThat(deletedOf(unassigned)).isTrue();
    }

    // ==================== 夹具 ====================

    /** 真实员工行：范围判定用 {@code seller_id} 指向的员工 id，靠测试事务回滚清理。 */
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
                .as("正式业务角色的验收账号禁止超管位").isFalse();
        return employeeId;
    }

    private void loginAs(Long employeeId) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("SEC 写范围 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        employee.setDepartmentId(1L);
        SmartRequestUtil.setRequestUser(employee);
    }

    private <T> T as(Long employeeId, Set<String> permissions, Supplier<T> body) {
        loginAs(employeeId);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            permissions.forEach(p -> stp.when(() -> StpUtil.hasPermission(p)).thenReturn(true));
            return body.get();
        }
    }

    /** 越权断言只断到异常类型与 {@code NO_PERMISSION} 语义（数字码的映射归属在底座）。 */
    private void assertNoPermission(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .isNotInstanceOf(ScmBusinessException.class)
                .hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
    }

    private Long createTradableCustomer(String suffix, Long ownerEmployeeId) {
        return as(ownerEmployeeId, Set.of(), () -> {
            CustomerAddForm form = new CustomerAddForm();
            form.setName("写范围测试" + suffix);
            form.setCustomerTypeId(customerTypeId("ENTERPRISE"));
            form.setSettleMode("INDEPENDENT");
            Long customerId = customerService.add(form);
            CustomerStatusForm status = new CustomerStatusForm();
            status.setCustomerId(customerId);
            status.setVersion(0);
            status.setStatus("COOPERATING");
            customerService.updateStatus(status);
            return customerId;
        });
    }

    /** 走真实建单服务造一张草稿，归属由客户快照强制为负责人。 */
    private Long draftOrder(Long customerId, Long skuId, Long ownerEmployeeId, String suffix) {
        return as(ownerEmployeeId, Set.of(), () -> salesOrderService.create(
                orderForm(customerId, skuId, suffix), prefix + "-so").getOrderId());
    }

    /** 造一张已确认订单：写路径门禁之前的完整主链，供退货族使用。 */
    private Long confirmedOrder(Long customerId, Long skuId, Long ownerEmployeeId, String suffix) {
        Long orderId = draftOrder(customerId, skuId, ownerEmployeeId, suffix);
        confirmOwn(orderId);
        return orderId;
    }

    /**
     * 以负责人身份把草稿推到 CONFIRMED。
     *
     * <p>夹具用的 SKU 是 {@code NON_STANDARD}（基类固定），提交时系统不自动补实数量，
     * 而确认前每行都必须有正实数量 —— 所以这里先走一次真实 {@code actualQuantity}，
     * 再提交 + 确认。这三步都是被本类当作「前置事实」的既有主链，不是待测点。
     */
    private void confirmOwn(Long orderId) {
        Long seller = jdbc.queryForObject("SELECT seller_id FROM sales_order WHERE id = ?", Long.class, orderId);
        Long itemId = firstItemId(orderId);
        as(seller, Set.of(), () -> {
            salesOrderService.submit(versionForm(orderId, versionOf(orderId)), prefix + "-sub");
            salesOrderService.actualQuantity(
                    actualForm(orderId, itemId, versionOf(orderId), "1.0000"), prefix + "-act");
            salesOrderService.confirm(versionForm(orderId, versionOf(orderId)), prefix + "-cfm");
            return null;
        });
    }

    private SalesOrderAddForm orderForm(Long customerId, Long skuId, String suffix) {
        SalesOrderAddForm form = new SalesOrderAddForm();
        form.setCustomerId(customerId);
        form.setOrderSource("ADMIN");
        form.setRemark("写范围测试单" + suffix);
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

    private SalesOrderUpdateForm updateForm(Long orderId, Integer version, Long customerId, Long skuId) {
        var form = new SalesOrderUpdateForm();
        form.setOrderId(orderId);
        form.setVersion(version);
        form.setCustomerId(customerId);
        form.setOrderSource("ADMIN");
        form.setRemark("写范围测试-编辑");
        OrderAddressForm address = new OrderAddressForm();
        address.setReceiverName("收货人");
        address.setReceiverPhone("13800000000");
        address.setAddress("测试地址");
        form.setAddress(address);
        SalesOrderItemForm item = new SalesOrderItemForm();
        item.setSkuId(skuId);
        item.setOrderedQuantity("2.0000");
        item.setManualPriceOverride(false);
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    private OrderVersionForm versionForm(Long orderId, Integer version) {
        OrderVersionForm form = new OrderVersionForm();
        form.setOrderId(orderId);
        form.setVersion(version);
        return form;
    }

    private OrderCancelForm cancelForm(Long orderId, Integer version) {
        OrderCancelForm form = new OrderCancelForm();
        form.setOrderId(orderId);
        form.setVersion(version);
        form.setReason("写范围测试取消");
        return form;
    }

    private OrderActualQuantityForm actualForm(Long orderId, Long itemId, Integer version, String quantity) {
        OrderActualQuantityForm form = new OrderActualQuantityForm();
        form.setOrderId(orderId);
        form.setItemId(itemId);
        form.setVersion(version);
        form.setActualQuantity(quantity);
        form.setReason("写范围测试实收");
        return form;
    }

    private OrderReturnAddForm returnAddForm(Long orderId, Long orderItemId, String quantity) {
        OrderReturnAddForm form = new OrderReturnAddForm();
        form.setOrderId(orderId);
        form.setReason("写范围测试退货");
        OrderReturnItemForm item = new OrderReturnItemForm();
        item.setOrderItemId(orderItemId);
        item.setRequestedQuantity(quantity);
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    private OrderReturnApproveForm approveForm(Long returnId, Integer version, Long orderItemId, String quantity) {
        OrderReturnApproveForm form = new OrderReturnApproveForm();
        form.setReturnId(returnId);
        form.setVersion(version);
        OrderReturnApproveItemForm item = new OrderReturnApproveItemForm();
        item.setOrderItemId(orderItemId);
        item.setApprovedQuantity(quantity);
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    private OrderReturnDecisionForm decisionForm(Long returnId, Integer version, String reason) {
        OrderReturnDecisionForm form = new OrderReturnDecisionForm();
        form.setReturnId(returnId);
        form.setVersion(version);
        form.setDecisionReason(reason);
        return form;
    }

    // ---------- 读侧小工具：全部直查库 + 清单缓存，绕开被测的写命令 ----------

    private Integer versionOf(Long orderId) {
        evicted();
        return jdbc.queryForObject("SELECT version FROM sales_order WHERE id = ?", Integer.class, orderId);
    }

    private String statusOf(Long orderId) {
        evicted();
        return jdbc.queryForObject("SELECT status FROM sales_order WHERE id = ?", String.class, orderId);
    }

    private String remarkOf(Long orderId) {
        evicted();
        return jdbc.queryForObject("SELECT remark FROM sales_order WHERE id = ?", String.class, orderId);
    }

    private Boolean deletedOf(Long orderId) {
        evicted();
        return jdbc.queryForObject("SELECT deleted FROM sales_order WHERE id = ?", Boolean.class, orderId);
    }

    private java.math.BigDecimal actualQuantityOf(Long itemId) {
        evicted();
        return jdbc.queryForObject(
                "SELECT actual_quantity FROM sales_order_item WHERE id = ?", java.math.BigDecimal.class, itemId);
    }

    private Long firstItemId(Long orderId) {
        evicted();
        return jdbc.queryForObject(
                "SELECT id FROM sales_order_item WHERE order_id = ? AND deleted = FALSE ORDER BY id LIMIT 1",
                Long.class, orderId);
    }

    private Integer returnVersionOf(Long returnId) {
        evicted();
        return jdbc.queryForObject("SELECT version FROM order_return WHERE id = ?", Integer.class, returnId);
    }

    private String returnStatusOf(Long returnId) {
        evicted();
        return jdbc.queryForObject("SELECT status FROM order_return WHERE id = ?", String.class, returnId);
    }

    private Integer reservationRowsFor(Long orderId) {
        evicted();
        return jdbc.queryForObject(
                "SELECT count(*) FROM inventory_reservation WHERE source_document_id = ?", Integer.class, orderId);
    }

    private Integer returnRowsFor(Long orderId) {
        evicted();
        return jdbc.queryForObject("SELECT count(*) FROM order_return WHERE order_id = ?", Integer.class, orderId);
    }

    /** 同一测试事务共用一个 SqlSession，MyBatis 一级缓存会按「语句 + 参数」命中旧结果。 */
    private void evicted() {
        session.clearCache();
    }
}
