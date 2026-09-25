package net.lab1024.sa.admin.module.scm.sorting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingActionForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryItemForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingTaskCreateForm;
import net.lab1024.sa.admin.module.scm.sorting.service.SortingTaskService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分拣的<b>真并发</b>验证（P1 完成标准里的「重复生成不会生成重复业务事实」与状态机竞态）。
 *
 * <p>单线程 IT 只能证明规则写对了，证不了竞态下仍成立：一条订单行被两个任务同时抢、
 * 同一明细被两个录入同时改、同一任务被两个人同时点完成 —— 分别由部分唯一索引、
 * 行级乐观锁与任务聚合锁兜底，只有独立事务同时打进去才压得到。
 *
 * <p><b>为什么必须 {@code Propagation.NOT_SUPPORTED}</b>：同一测试事务里的多个线程共享一条连接，
 * 互相看不见未提交数据，锁与唯一索引冲突都不会发生。代价是造数会提交到目标库，
 * 因此所有标识带 {@link #prefix} 随机前缀，幂等键也带前缀与纳秒。
 *
 * <p><b>竞态用例的断言不能猜谁输</b>：只断「成功数 + 失败码 + 最终账」三者一致，
 * 且最终值按「与成功那笔相符」而不是按提交顺序断言。
 *
 * <p>工作线程没有 Sa-Token 上下文，{@code StpUtil.hasPermission} 取失败关闭，
 * 因此这里刻意只走「自己建单派给自己」「受指派人本人」这类不需要放宽权限的路径。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("P1 分拣并发（PG IT，无外层事务）")
class SortingTaskConcurrencyPgIT extends ScmW6PgITBase {

    private static final long TIMEOUT_SECONDS = 60;

    @Autowired
    private SortingTaskService sorting;

    @Test
    @DisplayName("两个主管并发抢同一订单行建单：恰一成功，任务与明细都恰一条")
    void concurrentCreateCannotOccupySameOrderLine() throws Exception {
        Long warehouse = seedWarehouseId();
        Long leadA = newEmployee("CA");
        Long leadB = newEmployee("CB");
        grantWarehouseScope(leadA, warehouse);
        grantWarehouseScope(leadB, warehouse);
        var order = order("cc1", "5.0000");

        var outcomes = runConcurrently(List.of(
                () -> create(leadA, warehouse, "A", order.item()).id(),
                () -> create(leadB, warehouse, "B", order.item()).id()));

        assertThat(count("SELECT count(*) FROM sorting_task t JOIN sorting_task_item i ON i.task_id = t.id"
                        + " WHERE i.sales_order_item_id = ? AND i.deleted = FALSE", order.item()))
                .as("两次并发建单只允许落一个任务，失败那次不能留行").isEqualTo(1);
        assertThat(count("SELECT count(*) FROM sorting_task_item WHERE sales_order_item_id = ?", order.item()))
                .as("占用明细也恰一条").isEqualTo(1);
        outcomes.stream().filter(o -> o.failed).forEach(o ->
                assertThat(o.code).as("失败方必须是占用冲突，不能是死锁或超时").isEqualTo(41123));
    }

    @Test
    @DisplayName("并发录入同一明细行：恰一次生效，版本只前进一次")
    void concurrentEntryOnSameLineAppliesOnce() throws Exception {
        Long warehouse = seedWarehouseId();
        Long sorter = newEmployee("DA");
        grantWarehouseScope(sorter, warehouse);
        var order = order("cc2", "8.0000");
        Long taskId = create(sorter, warehouse, "T", order.item()).id();
        Long itemId = jdbc.queryForObject("SELECT id FROM sorting_task_item WHERE task_id = ?", Long.class, taskId);
        int version = itemVersion(itemId);

        var outcomes = runConcurrently(List.of(
                () -> enter(taskId, sorter, itemId, version, "3.0000"),
                () -> enter(taskId, sorter, itemId, version, "4.0000")));

        assertThat(outcomes.stream().filter(o -> !o.failed).count())
                .as("同一行的并发录入必须恰一次生效").isEqualTo(1);
        outcomes.stream().filter(o -> o.failed).forEach(o ->
                assertThat(o.code).as("第二次必须是乐观锁冲突，不能是静默覆盖").isEqualTo(40921));
        assertThat(itemVersion(itemId)).as("版本只前进一次：丢失更新或双计都会让它跳两格").isEqualTo(version + 1);
        assertThat(sortedQuantity(itemId).toPlainString())
                .as("最终值必须来自成功那一笔，不能是两次录入拼出来的第三种数").isIn("3.0000", "4.0000");
    }

    @Test
    @DisplayName("并发完成同一任务：恰一次落地，完成时间与版本都只前进一次")
    void concurrentCompleteAppliesOnce() throws Exception {
        Long warehouse = seedWarehouseId();
        Long sorter = newEmployee("EA");
        grantWarehouseScope(sorter, warehouse);
        var order = order("cc3", "6.0000");
        Long taskId = create(sorter, warehouse, "U", order.item()).id();
        Long itemId = jdbc.queryForObject("SELECT id FROM sorting_task_item WHERE task_id = ?", Long.class, taskId);
        enter(taskId, sorter, itemId, itemVersion(itemId), "6.0000");
        int version = taskVersion(taskId);

        var outcomes = runConcurrently(List.of(
                () -> complete(taskId, sorter, version),
                () -> complete(taskId, sorter, version)));

        assertThat(outcomes.stream().filter(o -> !o.failed).count()).as("完成只能生效一次").isEqualTo(1);
        outcomes.stream().filter(o -> o.failed).forEach(o ->
                assertThat(o.code).as("第二次必须是版本冲突").isEqualTo(40921));
        assertThat(taskStatus(taskId)).isEqualTo("COMPLETED");
        assertThat(taskVersion(taskId)).as("版本只前进一次").isEqualTo(version + 1);
    }

    // ------------------------------------------------------------------
    // 并发夹具
    // ------------------------------------------------------------------

    private record Outcome(boolean failed, Long id, int code) {
    }

    private record CreatedTask(Long id, Integer version) {
    }

    private record OrderRef(Long order, Long item) {
    }

    /**
     * 让 N 段动作在同一起跑线开始，各自跑在<b>独立事务</b>里。
     */
    private List<Outcome> runConcurrently(List<Callable<Long>> tasks) throws Exception {
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (var task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            var outcomes = new ArrayList<Outcome>();
            for (var future : futures) {
                try {
                    outcomes.add(new Outcome(false, future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), 0));
                } catch (ExecutionException failed) {
                    outcomes.add(new Outcome(true, null, code(failed.getCause())));
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private CreatedTask create(Long employeeId, Long warehouse, String tag, Long orderItemId) {
        var form = new SortingTaskCreateForm();
        form.setWarehouseId(warehouse);
        form.setAssigneeEmployeeId(employeeId);
        form.setRemark("P1 并发建单");
        form.setSalesOrderItemIds(List.of(orderItemId));
        loginAs(employeeId);
        try {
            var detail = sorting.create(form, prefix + ":create:" + tag + ":" + System.nanoTime());
            return new CreatedTask(detail.getTask().getId(), detail.getTask().getVersion());
        } finally {
            SmartRequestUtil.remove();
        }
    }

    private Long enter(Long taskId, Long employeeId, Long itemId, Integer version, String quantity) {
        var item = new SortingEntryItemForm();
        item.setId(itemId);
        item.setVersion(version);
        item.setSortedQuantity(new BigDecimal(quantity));
        item.setResult("SHORT");
        item.setReason("并发录入用例");
        var form = new SortingEntryForm();
        form.setItems(List.of(item));
        loginAs(employeeId);
        try {
            sorting.enter(taskId, form);
            return itemId;
        } finally {
            SmartRequestUtil.remove();
        }
    }

    private Long complete(Long taskId, Long employeeId, Integer version) {
        var form = new SortingActionForm();
        form.setVersion(version);
        loginAs(employeeId);
        try {
            sorting.complete(taskId, form);
            return taskId;
        } finally {
            SmartRequestUtil.remove();
        }
    }

    private OrderRef order(String tag, String quantity) {
        Long skuId = newOnShelfSku(tag);
        Long customerId = newCustomer();
        Long orderId = confirmedSalesOrder(customerId, skuId, quantity, quantity);
        return new OrderRef(orderId, confirmedSalesOrderItemId(orderId));
    }

    private void loginAs(Long employeeId) {
        var employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setLoginName("P1C" + employeeId);
        employee.setActualName("P1 分拣并发员工");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        employee.setDepartmentId(1L);
        SmartRequestUtil.setRequestUser(employee);
    }

    private Long newEmployee(String suffix) {
        String loginName = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder",
                "分拣并发" + suffix);
        return jdbc.queryForObject("SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
    }

    private void grantWarehouseScope(Long employeeId, Long warehouseId) {
        jdbc.update("INSERT INTO employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                employeeId, warehouseId);
    }

    private int code(Throwable error) {
        return error instanceof ScmBusinessException e ? e.getErrorCode().getCode() : -1;
    }

    private int itemVersion(Long itemId) {
        return jdbc.queryForObject("SELECT version FROM sorting_task_item WHERE id = ?", Integer.class, itemId);
    }

    private int taskVersion(Long taskId) {
        return jdbc.queryForObject("SELECT version FROM sorting_task WHERE id = ?", Integer.class, taskId);
    }

    private String taskStatus(Long taskId) {
        return jdbc.queryForObject("SELECT status FROM sorting_task WHERE id = ?", String.class, taskId);
    }

    private BigDecimal sortedQuantity(Long itemId) {
        return jdbc.queryForObject("SELECT sorted_quantity FROM sorting_task_item WHERE id = ?",
                BigDecimal.class, itemId);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }
}
