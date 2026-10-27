package com.xsy.scm.order;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.ScmW3PgITBase;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.domain.form.OrderActualQuantityForm;
import com.xsy.scm.order.domain.form.OrderAddressForm;
import com.xsy.scm.order.domain.form.OrderLockForm;
import com.xsy.scm.order.domain.form.OrderVersionForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderItemForm;
import com.xsy.scm.order.domain.form.SalesOrderUpdateForm;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.order.service.OrderLockService;
import com.xsy.scm.order.service.SalesOrderService;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * 锁单与发车后改单：锁是「改单要额外授权」的开关，不是新的订单状态。
 *
 * <p>
 * IT 线程没有 Sa-Token 上下文，{@code ScmDataScopeService#hasPermission} 会把权限判成 false（失败关闭）； 需要放行的用例按
 * {@code ScmOrderCustomerDataScopePgIT} 的既有做法用 {@code mockStatic(StpUtil.class)} 显式给权限。
 */
@DisplayName("订单锁单与发车后改单（PG IT）")
class OrderLockPgIT extends ScmW3PgITBase {

    @Autowired
    SalesOrderService orders;
    @Autowired
    OrderLockService locks;
    @Autowired
    CustomerService customers;
    @Autowired
    SalesOrderDao salesOrderDao;
    @Autowired
    SqlSession session;

    private Long customer() {
        var f = new CustomerAddForm();
        f.setName(prefix);
        f.setCustomerTypeId(customerTypeId("ENTERPRISE"));
        f.setSettleMode("INDEPENDENT");
        f.setSellerId(anyEmployeeId());
        f.setContactName("收货人");
        f.setContactPhone("13800000000");
        f.setAddress("测试地址");
        Long id = customers.add(f);
        var s = new CustomerStatusForm();
        s.setCustomerId(id);
        s.setVersion(0);
        s.setStatus("COOPERATING");
        customers.updateStatus(s);
        return id;
    }

    private SalesOrderAddForm form(Long c, Long sku) {
        var f = new SalesOrderAddForm();
        f.setCustomerId(c);
        f.setOrderSource("ADMIN");
        f.setRemark("测试订单");
        var a = new OrderAddressForm();
        a.setReceiverName("测试客户");
        a.setReceiverPhone("13800000000");
        a.setAddress("测试地址");
        f.setAddress(a);
        var i = new SalesOrderItemForm();
        i.setSkuId(sku);
        i.setOrderedQuantity("2.0000");
        i.setManualPriceOverride(false);
        f.setItems(new ArrayList<>(List.of(i)));
        return f;
    }

    private OrderVersionForm version(SalesOrderDetailVO o) {
        var f = new OrderVersionForm();
        f.setOrderId(o.getOrderId());
        f.setVersion(o.getVersion());
        return f;
    }

    private SalesOrderDetailVO confirmed(Long c, Long sku) {
        var o = orders.create(form(c, sku), prefix + "c-" + UUID.randomUUID());
        o = orders.submit(version(o), prefix + "s-" + UUID.randomUUID());
        var a = new OrderActualQuantityForm();
        a.setOrderId(o.getOrderId());
        a.setItemId(o.getItems().getFirst().getItemId());
        a.setVersion(o.getItems().getFirst().getVersion());
        a.setActualQuantity("1.5000");
        a.setReason("实际称重");
        o = orders.actualQuantity(a, prefix + "a-" + UUID.randomUUID());
        return orders.confirm(version(o), prefix + "f-" + UUID.randomUUID());
    }

    private static SalesOrderUpdateForm updateOf(SalesOrderAddForm base, SalesOrderDetailVO current, String quantity) {
        var u = new SalesOrderUpdateForm();
        org.springframework.beans.BeanUtils.copyProperties(base, u);
        u.setOrderId(current.getOrderId());
        u.setVersion(current.getVersion());
        u.getItems().getFirst().setItemId(current.getItems().getFirst().getItemId());
        u.getItems().getFirst().setVersion(current.getItems().getFirst().getVersion());
        u.getItems().getFirst().setOrderedQuantity(quantity);
        return u;
    }

    private static OrderLockForm lockForm(SalesOrderDetailVO o) {
        var f = new OrderLockForm();
        f.setOrderId(o.getOrderId());
        f.setVersion(o.getVersion());
        f.setReason("对账前锁单");
        return f;
    }

    /** 以显式权限执行一段逻辑：未点名的权限码取 Mockito 默认值 false，与生产的失败关闭一致。 */
    private <T> T as(String permission, Supplier<T> body) {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission(permission)).thenReturn(true);
            return body.get();
        }
    }

    @Test
    @DisplayName("锁单后普通改单被拒，解锁后可改；两次操作都写日志")
    void lockGatesEditsUntilUnlocked() {
        var c = customer();
        var sku = newOnShelfSku("LOCK");
        var order = confirmed(c, sku);
        assertThat(order.getLockedFlag()).isFalse();
        assertThat(order.getDispatchedAt()).isNull();
        assertThat(order.getEditable()).isTrue();

        var locked = locks.lock(lockForm(order));
        assertThat(locked.getLockedFlag()).isTrue();
        assertThat(locked.getLockReason()).isEqualTo(OrderLockService.MANUAL_REASON);
        assertThat(locked.getStatus()).isEqualTo("CONFIRMED");
        // 当前调用者没有「锁单后改单」权限，后端直接标记为不可改，前端按钮据此禁用。
        assertThat(locked.getEditable()).isFalse();
        assertThat(locked.getEditBlockedReason()).isEqualTo("订单已锁单，请新建加单");

        expectCode(() -> locks.lock(lockForm(locked)), 40912);
        expectCode(() -> orders.update(updateOf(form(c, sku), locked, "3.0000")), 40912);
        assertThat(jdbc.queryForObject("SELECT ordered_quantity FROM xsy_sales_order_item WHERE id=?",
                java.math.BigDecimal.class, order.getItems().getFirst().getItemId())).isEqualByComparingTo("2.0000");

        var unlocked = locks.unlock(lockForm(locked));
        assertThat(unlocked.getLockedFlag()).isFalse();
        assertThat(unlocked.getLockedAt()).isNull();
        assertThat(unlocked.getEditable()).isTrue();

        var edited = orders.update(updateOf(form(c, sku), unlocked, "3.0000"));
        assertThat(edited.getItems().getFirst().getOrderedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(edited.getOrderedTotalAmount()).isEqualByComparingTo("3.6000");
        // 非标品的实数量是称重结果，改单不覆盖，但结算金额按新单价重算。
        assertThat(edited.getItems().getFirst().getActualQuantity()).isEqualByComparingTo("1.5000");
        assertThat(edited.getSettlementTotalAmount()).isEqualByComparingTo("1.8000");
        assertThat(logCount(order.getOrderId(), "LOCK")).isEqualTo(1);
        assertThat(logCount(order.getOrderId(), "UNLOCK")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT operator FROM xsy_order_operation_log WHERE order_id=? AND operation_type='LOCK'", String.class,
                order.getOrderId())).isEqualTo("1:1");
    }

    @Test
    @DisplayName("未持发车后改单权限时改单被拒，订单行保持不变")
    void dispatchedEditIsRefusedWithoutPermission() {
        var c = customer();
        var sku = newOnShelfSku("NOPERM");
        var order = confirmed(c, sku);
        salesOrderDao.markDispatched(order.getOrderId(), OffsetDateTime.now(), "1:1");
        session.clearCache();
        // markDispatched 自己会推进版本号，夹具按服务端的新版本继续。
        order.setVersion(order.getVersion() + 1);

        expectCode(() -> orders.update(updateOf(form(c, sku), order, "2.5000")), 40913);
        assertThat(jdbc.queryForObject("SELECT ordered_quantity FROM xsy_sales_order_item WHERE id=?",
                java.math.BigDecimal.class, order.getItems().getFirst().getItemId())).isEqualByComparingTo("2.0000");
    }

    @Test
    @DisplayName("持发车后改单权限时改单成功，并单独记发车后改单日志")
    void dispatchedEditWithPermissionIsLoggedSeparately() {
        var c = customer();
        var sku = newOnShelfSku("AFTERDISPATCH");
        var order = confirmed(c, sku);
        salesOrderDao.markDispatched(order.getOrderId(), OffsetDateTime.now(), "1:1");
        session.clearCache();
        order.setVersion(order.getVersion() + 1);
        var dispatched = jdbc.queryForObject("SELECT dispatched_at FROM xsy_sales_order WHERE id=?",
                OffsetDateTime.class, order.getOrderId());
        assertThat(dispatched).isNotNull();

        var edited = as(OrderPermission.EDIT_AFTER_DISPATCH,
                () -> orders.update(updateOf(form(c, sku), order, "2.5000")));

        assertThat(edited.getItems().getFirst().getOrderedQuantity()).isEqualByComparingTo("2.5000");
        // 发车后改单同样沿用锁定单价，不静默改价。
        assertThat(edited.getItems().getFirst().getLockedUnitPrice())
                .isEqualByComparingTo(order.getItems().getFirst().getLockedUnitPrice());
        // 结算金额按称重实数量 × 锁定单价，改下单量不改已称出的实数量。
        assertThat(edited.getSettlementTotalAmount()).isEqualByComparingTo("1.8000");
        assertThat(logCount(order.getOrderId(), "EDIT_AFTER_DISPATCH")).isEqualTo(1);
    }

    @Test
    @DisplayName("已确认未发车、未锁的订单按普通改单权限可改")
    void confirmedOrderWithoutDispatchStaysEditable() {
        var c = customer();
        var sku = newOnShelfSku("EDITABLE");
        var order = confirmed(c, sku);

        var edited = orders.update(updateOf(form(c, sku), order, "4.0000"));

        assertThat(edited.getItems().getFirst().getOrderedQuantity()).isEqualByComparingTo("4.0000");
        assertThat(edited.getOrderedTotalAmount()).isEqualByComparingTo("4.8000");
        assertThat(logCount(order.getOrderId(), "UPDATE")).isEqualTo(1);
    }

    @Test
    @DisplayName("发车满 7 天的订单自动锁单，未满 7 天与未发车的不进候选")
    void autoLockOnlyTouchesOrdersDispatchedSevenDaysAgo() {
        var c = customer();
        var sku = newOnShelfSku("AUTO");
        var old = confirmed(c, sku);
        var recent = confirmed(c, sku);
        var notDispatched = confirmed(c, sku);
        jdbc.update("UPDATE xsy_sales_order SET dispatched_at = CURRENT_TIMESTAMP - INTERVAL '8 days' WHERE id=?",
                old.getOrderId());
        jdbc.update("UPDATE xsy_sales_order SET dispatched_at = CURRENT_TIMESTAMP - INTERVAL '6 days' WHERE id=?",
                recent.getOrderId());
        session.clearCache();

        assertThat(salesOrderDao.autoLockCandidates(1000)).contains(old.getOrderId()).doesNotContain(
                recent.getOrderId(), notDispatched.getOrderId());

        assertThat(locks.autoLock(old.getOrderId())).isTrue();
        // 重复调用不会重复锁、不会重复写日志。
        assertThat(locks.autoLock(old.getOrderId())).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT locked_flag || ':' || lock_reason || ':' || locked_by FROM xsy_sales_order WHERE id=?",
                String.class, old.getOrderId())).isEqualTo("true:" + OrderLockService.AUTO_7_DAYS_REASON + ":"
                    + OrderLockService.AUTO_LOCK_OPERATOR);
        assertThat(jdbc.queryForObject("SELECT auto_lock_at IS NOT NULL FROM xsy_sales_order WHERE id=?",
                Boolean.class, old.getOrderId())).isTrue();
        assertThat(logCount(old.getOrderId(), "AUTO_LOCK")).isEqualTo(1);
        assertThat(logCount(recent.getOrderId(), "AUTO_LOCK")).isZero();
        assertThat(jdbc.queryForObject("SELECT locked_flag FROM xsy_sales_order WHERE id=?", Boolean.class,
                notDispatched.getOrderId())).isFalse();
    }

    @Test
    @DisplayName("持锁单后改单权限时，锁住的订单仍可改单并留痕")
    void editLockedOrderWithExplicitPermission() {
        var c = customer();
        var sku = newOnShelfSku("EDITLOCKED");
        var order = confirmed(c, sku);
        var locked = locks.lock(lockForm(order));

        var edited = as(OrderPermission.EDIT_LOCKED,
                () -> orders.update(updateOf(form(c, sku), locked, "3.0000")));

        assertThat(edited.getLockedFlag()).isTrue();
        assertThat(edited.getItems().getFirst().getOrderedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(logCount(order.getOrderId(), "UPDATE")).isEqualTo(1);
    }

    private int logCount(Long orderId, String operation) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM xsy_order_operation_log WHERE order_id=? AND operation_type=?", Integer.class,
                orderId, operation);
    }
}
