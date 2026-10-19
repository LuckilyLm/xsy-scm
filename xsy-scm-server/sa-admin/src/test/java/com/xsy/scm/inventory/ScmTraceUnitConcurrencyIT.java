package com.xsy.scm.inventory;

import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptConfirmForm;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.trace.domain.form.TraceUnitBindForm;
import com.xsy.scm.trace.domain.form.TraceUnitIssueForm;
import com.xsy.scm.trace.domain.form.TraceUnitVoidForm;
import com.xsy.scm.trace.service.TraceUnitService;
import com.xsy.scm.trace.service.TraceCorrectionService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.xsy.scm.warehouse.domain.form.WarehouseAddForm;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 包装码的并发完整性。
 *
 * <p>
 * 关掉测试事务（{@code NOT_SUPPORTED}）才有两个真正独立的事务：塞进同一个事务里会共享连接、互相看不见，
 * 压不到任何行锁。造数提交到开发库，但都用随机前缀隔离。
 *
 * <p>
 * 压三处：发码的「已发 + 本次 ≤ 余额」、绑定与作废对同一个码的竞态、两枚码同时绑定同一分拣行的累计上限。
 * 三处都靠行锁串行化，锁序写错会在这里显形（死锁会让用例超时而不是偶发通过）。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("W9 包装码并发（PG IT，无外层事务）")
class ScmTraceUnitConcurrencyIT extends ScmW6PgITBase {

    private static final long TIMEOUT_SECONDS = 60;

    @Autowired
    private TraceUnitService traceUnitService;

    @Autowired
    private TraceCorrectionService traceCorrectionService;

    private Long lotWarehouseId;

    @BeforeEach
    void createLotWarehouse() {
        WarehouseAddForm form = new WarehouseAddForm();
        form.setName(prefix + "并发批次仓");
        form.setLotTrackingEnabled(true);
        lotWarehouseId = warehouseService.create(form);
    }

    @Override
    protected Long fixtureWarehouseId() {
        return lotWarehouseId;
    }

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
    }

    private PurchaseReceiptVO confirmWithLots(Long receiptId, String quantity, String lotQuantity) {
        PurchaseReceiptVO current = reloadReceipt(receiptId);
        PurchaseReceiptItemVO line = current.getItems().getFirst();
        PurchaseReceiptConfirmForm.Item item = receiptLine(line.getId(), line.getVersion(), quantity);
        PurchaseReceiptConfirmForm.Lot lot = new PurchaseReceiptConfirmForm.Lot();
        lot.setSourceLotNo("CONC-" + quantity);
        lot.setQuantity(lotQuantity);
        item.setLots(List.of(lot));
        return purchaseReceiptService.confirm(confirmForm(current.getId(), current.getVersion(), item),
                prefix + ":conc:" + UUID.randomUUID());
    }

    private Long lotIdOf(Long skuId) {
        return jdbc.queryForObject("SELECT id FROM xsy_trace_lot WHERE sku_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, skuId);
    }

    private TraceUnitIssueForm issueForm(Long lotId, int count, String perPackage) {
        TraceUnitIssueForm form = new TraceUnitIssueForm();
        form.setLotId(lotId);
        form.setPackageType("BAG");
        form.setPackageCount(count);
        form.setQuantityPerPackage(perPackage);
        return form;
    }

    /**
     * 工作线程没有父线程的登录上下文（{@code SmartRequestUtil} 是 ThreadLocal），
     * 而数据范围解析会因「取不到登录员工」失败关闭 —— 必须在线程内重新挂身份。
     */
    private Runnable withOperator(Runnable task) {
        return () -> {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("W9 conc");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            SmartRequestUtil.setRequestUser(employee);
            try {
                task.run();
            } finally {
                SmartRequestUtil.remove();
            }
        };
    }

    /** 并发跑两个动作，返回各自的结果（成功为 null，失败为异常）。 */
    private List<Throwable> runConcurrently(Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = new ArrayList<>();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable task : List.of(withOperator(first), withOperator(second))) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        task.run();
                    } catch (Throwable error) {
                        synchronized (failures) {
                            failures.add(error);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return failures;
    }

    @Test
    @DisplayName("#1 两事务同时发码：只能成功一个，发码总量不超过批次余额")
    void concurrentIssueCannotExceedBalance() throws Exception {
        Long skuId = newOnShelfSku("CLOT1");
        W6Fixture fx = inboundFixture("CLOT1", skuId, "10.0000");
        confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
        Long lotId = lotIdOf(skuId);

        List<Throwable> failures = runConcurrently(
                () -> traceUnitService.issue(issueForm(lotId, 7, "1.0000"), "W9 conc A"),
                () -> traceUnitService.issue(issueForm(lotId, 7, "1.0000"), "W9 conc B"));

        // 恰好一个成功：另一个必须被 41504 拒绝
        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst()).isInstanceOf(ScmBusinessException.class);
        assertThat(((ScmBusinessException) failures.getFirst()).getErrorCode().getCode()).isEqualTo(41504);

        BigDecimal issued = jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM xsy_trace_unit WHERE lot_id = ?", BigDecimal.class, lotId);
        assertThat(issued).isLessThanOrEqualTo(new BigDecimal("10.0000"));
        assertThat(issued).isEqualByComparingTo("7.0000");
    }

    @Test
    @DisplayName("#2 绑定与作废并发：只能成功一个，且不会出现「已作废的码挂着绑定」")
    void concurrentBindAndVoidCannotBothSucceed() throws Exception {
        Long skuId = newOnShelfSku("CLOT2");
        W6Fixture fx = inboundFixture("CLOT2", skuId, "10.0000");
        confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
        Long lotId = lotIdOf(skuId);
        String unitCode = traceUnitService.issue(issueForm(lotId, 1, "1.0000"), "W9 conc").getFirst().getUnitCode();

        Long customerId = newCustomer();
        Long salesOrderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
        sortingCompletedFor(salesOrderId);
        Long itemId = jdbc.queryForObject(
                "SELECT id FROM xsy_sorting_task_item WHERE sales_order_id = ? AND sku_id = ? "
                        + "ORDER BY id DESC LIMIT 1", Long.class, salesOrderId, skuId);

        TraceUnitBindForm bind = new TraceUnitBindForm();
        bind.setUnitCode(unitCode);
        bind.setSortingTaskItemId(itemId);
        TraceUnitVoidForm voidForm = new TraceUnitVoidForm();
        voidForm.setUnitCode(unitCode);
        voidForm.setReason("并发作废");

        List<Throwable> failures = runConcurrently(
                () -> traceUnitService.bind(bind, "W9 conc bind"),
                () -> traceUnitService.voidUnit(voidForm, "W9 conc void"));

        assertThat(failures).hasSize(1);
        int code = ((ScmBusinessException) failures.getFirst()).getErrorCode().getCode();
        assertThat(code).isIn(41512, 41511);

        // 终态必须自洽：要么「有效 + 已绑定」，要么「已作废 + 无绑定」
        String status = jdbc.queryForObject("SELECT status FROM xsy_trace_unit WHERE unit_code = ?", String.class,
                unitCode);
        Integer bindings = jdbc.queryForObject(
                "SELECT count(*) FROM xsy_trace_unit_binding b JOIN xsy_trace_unit u ON u.id = b.unit_id "
                        + "WHERE u.unit_code = ?", Integer.class, unitCode);
        if ("VOID".equals(status)) {
            assertThat(bindings).as("已作废的码不能留下绑定").isZero();
        } else {
            assertThat(status).isEqualTo("ACTIVE");
            assertThat(bindings).as("绑定成功的码必须有效").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("#4 旁路 SQL 并发插入同一码绑定：数据库行锁仍必须保证唯一有效绑定")
    void concurrentDirectSqlBindCannotBypassDatabaseGuard() throws Exception {
        Long skuId = newOnShelfSku("CLOT4");
        W6Fixture fx = inboundFixture("CLOT4", skuId, "10.0000");
        confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
        Long lotId = lotIdOf(skuId);
        var unit = traceUnitService.issue(issueForm(lotId, 1, "1.0000"), "W9 sql guard").getFirst();
        Long customerId = newCustomer();
        Long salesOrderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
        sortingCompletedFor(salesOrderId);
        Long itemId = jdbc.queryForObject(
                "SELECT id FROM xsy_sorting_task_item WHERE sales_order_id = ? AND sku_id = ? "
                        + "ORDER BY id DESC LIMIT 1", Long.class, salesOrderId, skuId);

        Runnable directInsert = () -> jdbc.update(
                "INSERT INTO xsy_trace_unit_binding "
                        + "(unit_id, lot_id, sorting_task_id, sorting_task_item_id, sales_order_id, "
                        + "sales_order_item_id, customer_id, customer_name_snapshot, order_no_snapshot, "
                        + "bound_at, bound_by, created_by) "
                        + "SELECT ?, ?, i.task_id, i.id, i.sales_order_id, i.sales_order_item_id, "
                        + "i.customer_id, i.customer_name_snapshot, i.order_no_snapshot, "
                        + "CURRENT_TIMESTAMP, 'W9 bypass', 'W9 bypass' "
                        + "FROM xsy_sorting_task_item i WHERE i.id = ?",
                unit.getId(), lotId, itemId);
        List<Throwable> failures = runConcurrently(directInsert, directInsert);
        assertThat(failures).hasSize(1);
        Integer active = jdbc.queryForObject(
                "SELECT count(*) FROM xsy_trace_unit_binding b "
                        + "WHERE b.unit_id = ? AND NOT EXISTS "
                        + "(SELECT 1 FROM xsy_trace_unit_unbind ub WHERE ub.binding_id = b.id)",
                Integer.class, unit.getId());
        assertThat(active).isEqualTo(1);
    }

    @Test
    @DisplayName("#5 已绑定包装码禁止被旁路 SQL 直接作废")
    void directSqlCannotVoidBoundUnit() {
        Long skuId = newOnShelfSku("CLOT5");
        W6Fixture fx = inboundFixture("CLOT5", skuId, "10.0000");
        confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
        Long lotId = lotIdOf(skuId);
        String code = traceUnitService.issue(issueForm(lotId, 1, "1.0000"), "W9 void guard")
                .getFirst().getUnitCode();
        Long customerId = newCustomer();
        Long salesOrderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
        sortingCompletedFor(salesOrderId);
        Long itemId = jdbc.queryForObject(
                "SELECT id FROM xsy_sorting_task_item WHERE sales_order_id = ? AND sku_id = ? "
                        + "ORDER BY id DESC LIMIT 1", Long.class, salesOrderId, skuId);
        TraceUnitBindForm bind = new TraceUnitBindForm();
        bind.setUnitCode(code);
        bind.setSortingTaskItemId(itemId);
        traceUnitService.bind(bind, "W9 void guard");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE xsy_trace_unit SET status = 'VOID' WHERE unit_code = ?", code))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM xsy_trace_unit WHERE unit_code = ?", String.class, code))
                .isEqualTo("ACTIVE");

        Long unitId = jdbc.queryForObject("SELECT id FROM xsy_trace_unit WHERE unit_code = ?",
                Long.class, code);
        Long bindingId = jdbc.queryForObject("SELECT id FROM xsy_trace_unit_binding WHERE unit_id = ?",
                Long.class, unitId);
        // 不存在对应待审批更正单的 SQL 解绑也必须被数据库直接拒绝。
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO xsy_trace_unit_unbind "
                        + "(unit_id, binding_id, correction_id, reason, unbound_at, unbound_by, created_by) "
                        + "VALUES (?, ?, -1, 'bypass', CURRENT_TIMESTAMP, 'bypass', 'bypass')",
                unitId, bindingId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("#6 正式出库之后，服务与旁路 SQL 均不能解绑或补绑包装码")
    void outboundPreventsCorrectionAndDirectSqlRebinding() {
        Long skuId = newOnShelfSku("CLOT6");
        W6Fixture fx = inboundFixture("CLOT6", skuId, "10.0000");
        confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
        Long lotId = lotIdOf(skuId);
        var units = traceUnitService.issue(issueForm(lotId, 2, "1.0000"), "W9 outbound guard");
        Long customerId = newCustomer();
        Long salesOrderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
        sortingCompletedFor(salesOrderId);
        Long itemId = jdbc.queryForObject(
                "SELECT id FROM xsy_sorting_task_item WHERE sales_order_id = ? AND sku_id = ? "
                        + "ORDER BY id DESC LIMIT 1", Long.class, salesOrderId, skuId);
        TraceUnitBindForm bind = new TraceUnitBindForm();
        bind.setUnitCode(units.getFirst().getUnitCode());
        bind.setSortingTaskItemId(itemId);
        traceUnitService.bind(bind, "W9 bound");

        var apply = new com.xsy.scm.trace.domain.form.TraceCorrectionApplyForm();
        apply.setUnitCode(units.getFirst().getUnitCode());
        apply.setReason("发车前贴错码");
        var correction = traceCorrectionService.apply(apply, "W9 applicant");

        Long salesOrderItemId = jdbc.queryForObject(
                "SELECT sales_order_item_id FROM xsy_sorting_task_item WHERE id = ?",
                Long.class, itemId);
        Long outboundId = jdbc.queryForObject(
                "INSERT INTO xsy_inventory_outbound"
                        + "(outbound_no, warehouse_id, status, confirmed_at, operator, created_by) "
                        + "VALUES (?, ?, 'CONFIRMED', CURRENT_TIMESTAMP, 'W9 sql', 'W9 sql') RETURNING id",
                Long.class, "W9SQL-" + UUID.randomUUID(), lotWarehouseId);
        jdbc.update("INSERT INTO xsy_inventory_outbound_item"
                        + "(outbound_id, sku_id, quantity, sales_order_id, sales_order_item_id, created_by) "
                        + "VALUES (?, ?, 1, ?, ?, 'W9 sql')",
                outboundId, skuId, salesOrderId, salesOrderItemId);

        var decision = new com.xsy.scm.trace.domain.form.TraceCorrectionDecisionForm();
        decision.setId(correction.getId());
        decision.setVersion(correction.getVersion());
        assertThatThrownBy(() -> traceCorrectionService.approve(decision, "W9 approver"))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                        .isEqualTo(41525));

        Long bindingId = jdbc.queryForObject(
                "SELECT id FROM xsy_trace_unit_binding WHERE unit_id = ?",
                Long.class, units.getFirst().getId());
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO xsy_trace_unit_unbind "
                        + "(unit_id, binding_id, correction_id, reason, unbound_at, unbound_by, created_by) "
                        + "VALUES (?, ?, ?, 'bypass', CURRENT_TIMESTAMP, 'W9 approver', 'W9 approver')",
                units.getFirst().getId(), bindingId, correction.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        TraceUnitBindForm lateBind = new TraceUnitBindForm();
        lateBind.setUnitCode(units.get(1).getUnitCode());
        lateBind.setSortingTaskItemId(itemId);
        assertThatThrownBy(() -> traceUnitService.bind(lateBind, "W9 late"))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                        .isEqualTo(41526));

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO xsy_trace_unit_binding "
                        + "(unit_id, lot_id, sorting_task_id, sorting_task_item_id, sales_order_id, "
                        + "sales_order_item_id, customer_id, customer_name_snapshot, order_no_snapshot, "
                        + "bound_at, bound_by, created_by) "
                        + "SELECT ?, ?, i.task_id, i.id, i.sales_order_id, i.sales_order_item_id, "
                        + "i.customer_id, i.customer_name_snapshot, i.order_no_snapshot, "
                        + "CURRENT_TIMESTAMP, 'W9 bypass', 'W9 bypass' "
                        + "FROM xsy_sorting_task_item i WHERE i.id = ?",
                units.get(1).getId(), lotId, itemId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("#3 两枚码同时绑定同一分拣行：累计不得超过实拣量")
    void concurrentBindCannotExceedSortedQuantity() throws Exception {
        Long skuId = newOnShelfSku("CLOT3");
        W6Fixture fx = inboundFixture("CLOT3", skuId, "20.0000");
        confirmWithLots(fx.receipt().getId(), "20.0000", "20.0000");
        Long lotId = lotIdOf(skuId);
        var units = traceUnitService.issue(issueForm(lotId, 2, "6.0000"), "W9 conc");

        Long customerId = newCustomer();
        Long salesOrderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
        sortingCompletedFor(salesOrderId);
        Long itemId = jdbc.queryForObject(
                "SELECT id FROM xsy_sorting_task_item WHERE sales_order_id = ? AND sku_id = ? "
                        + "ORDER BY id DESC LIMIT 1", Long.class, salesOrderId, skuId);

        TraceUnitBindForm first = new TraceUnitBindForm();
        first.setUnitCode(units.get(0).getUnitCode());
        first.setSortingTaskItemId(itemId);
        TraceUnitBindForm second = new TraceUnitBindForm();
        second.setUnitCode(units.get(1).getUnitCode());
        second.setSortingTaskItemId(itemId);

        List<Throwable> failures = runConcurrently(
                () -> traceUnitService.bind(first, "W9 conc bind A"),
                () -> traceUnitService.bind(second, "W9 conc bind B"));

        // 每枚 6、实拣 10：只能成功一枚
        assertThat(failures).hasSize(1);
        assertThat(((ScmBusinessException) failures.getFirst()).getErrorCode().getCode()).isEqualTo(41509);

        BigDecimal bound = jdbc.queryForObject(
                "SELECT COALESCE(SUM(u.quantity), 0) FROM xsy_trace_unit_binding b "
                        + "JOIN xsy_trace_unit u ON u.id = b.unit_id WHERE b.sorting_task_item_id = ?",
                BigDecimal.class, itemId);
        assertThat(bound).isLessThanOrEqualTo(new BigDecimal("10.0000"));
    }
}
