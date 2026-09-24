package net.lab1024.sa.admin.module.scm.sorting;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryEligibilityPolicy;
import net.lab1024.sa.admin.module.scm.order.dao.SalesOrderDao;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingActionForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingAssignForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryItemForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingSummaryQueryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingTaskCreateForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingTaskQueryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingTaskItemVO;
import net.lab1024.sa.admin.module.scm.sorting.service.SortingQueryService;
import net.lab1024.sa.admin.module.scm.sorting.service.SortingTaskService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.base.common.util.SmartRequestUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * P1 分拣：任务状态机、幂等与占用、数据范围，以及交给配送资格的那一半口径。
 *
 * <p>逐条对上的裁决（{@code docs/decisions.md}「P1 分拣管理裁决」第 1–3、6、8、10、11、16–19 条与
 * 补充第 15、17、18 条）：
 * <ul>
 *   <li>计划量是冻结快照，<b>绝不回写</b>订单行的 {@code actual_quantity} 与结算金额（第 1、3 条）；</li>
 *   <li>分拣不写库存：余额、流水、出库单都不动（第 6 条）；</li>
 *   <li>重复生成不产生重复业务事实：同幂等键重放返回同一任务，抢同一订单行的另一张单被拒（第 2 条）；</li>
 *   <li>完成必须全部活动明细都有结果（补充第 17 条）；重开保留已录内容（补充第 18 条）；</li>
 *   <li>范围 = 授权仓 ∩ 受指派人，未指派任务只对队列管理者可见（第 7 条与补充第 15 条）；</li>
 *   <li>打印只计次（第 8 条）；资格按「全部有效行被已完成任务覆盖」判定（第 11 条与补充第 18 条）。</li>
 * </ul>
 *
 * <p><b>身份与权限</b>：员工用裸 SQL 造且 {@code administrator_flag=false}（超管跑绿不算权限证据）；
 * 功能权限按既有做法用 {@code mockStatic(StpUtil)} 显式点名，未点名的取默认 false 即失败关闭；
 * 仓库授权走行配置表 {@code employee_warehouse_scope}，因为「范围是配置值」本身就是被验收对象。
 * {@link #as} 不可嵌套调用。
 */
@DisplayName("P1 分拣任务状态机、占用、范围与资格交接（PG IT）")
class SortingTaskPgIT extends ScmW6PgITBase {

    private static final String ADD_PERM = "scm:sorting:task:add";
    private static final String ASSIGN_PERM = "scm:sorting:task:assign";
    private static final String QUERY_PERM = "scm:sorting:task:query";
    private static final String ENTRY_PERM = "scm:sorting:item:update";
    private static final String COMPLETE_PERM = "scm:sorting:task:complete";
    private static final String CANCEL_PERM = "scm:sorting:task:cancel";
    private static final String REOPEN_PERM = "scm:sorting:task:reopen";
    private static final String PRINT_PERM = "scm:sorting:task:print";
    private static final String SUMMARY_PERM = "scm:sorting:summary:query";

    private static final Set<String> QUEUE_MANAGER = Set.of(ADD_PERM, ASSIGN_PERM, CANCEL_PERM, REOPEN_PERM, QUERY_PERM);
    private static final Set<String> SORTER = Set.of(QUERY_PERM, ENTRY_PERM, COMPLETE_PERM, PRINT_PERM);

    @Autowired
    private SortingTaskService sorting;

    @Autowired
    private SortingQueryService queries;

    @Autowired
    private DeliveryEligibilityPolicy eligibility;

    @Autowired
    private SalesOrderDao orders;

    private Long warehouse;
    private Long lead;
    private Long sorterA;
    private Long sorterB;

    @BeforeEach
    void setUpActors() {
        warehouse = seedWarehouseId();
        lead = newEmployee("LEAD");
        sorterA = newEmployee("SA");
        sorterB = newEmployee("SB");
        grantWarehouseScope(lead);
        grantWarehouseScope(sorterA);
        grantWarehouseScope(sorterB);
    }

    // ------------------------------------------------------------------
    // 建单：快照、幂等与占用
    // ------------------------------------------------------------------

    @Test
    @DisplayName("建单冻结计划量并留快照；同幂等键重放不生成第二套事实")
    void createFreezesPlanSnapshotAndReplayIsIdempotent() {
        var order = order("c1", "10.0000");
        var form = createForm("c1", List.of(order.item()), sorterA);
        String key = key("c1");

        var first = as(lead, QUEUE_MANAGER, () -> sorting.create(form, key));
        var replay = as(lead, QUEUE_MANAGER, () -> sorting.create(form, key));

        assertThat(replay.getTask().getId()).isEqualTo(first.getTask().getId());
        // 只按「这一条订单行」数，不按整张表数：并发类用例会往同一张表提交行。
        assertThat(tasksOccupying(order.item())).as("同一请求重放只能占用一次").isEqualTo(1);
        var item = first.getItems().getFirst();
        assertThat(item.getPlannedQuantitySnapshot()).isEqualByComparingTo("10.0000");
        assertThat(item.getSortedQuantity()).isNull();
        assertThat(item.getResult()).isNull();
        assertThat(item.getOccupationStatus()).isEqualTo("ACTIVE");
        assertThat(first.getTask().getStatus()).isEqualTo("PENDING");
        assertThat(first.getTask().getWarehouseNameSnapshot()).isNotBlank();
        assertThat(first.getTask().getTaskNo()).startsWith("SRT").hasSize(17);
        assertThat(actualQuantity(order)).as("建单不得改订单实发量").isEqualByComparingTo("10.0000");
    }

    /**
     * 占用互斥的用例<b>必须止于那条被拒的建单</b>：唯一索引冲突会把 PostgreSQL 的共享事务打进
     * aborted 态（25P02），之后任何回读都只会拿到「current transaction is aborted」。
     * 「被拒的那次不留任务行」由 {@code SortingTaskConcurrencyPgIT} 在各自独立事务里取证。
     */
    @Test
    @DisplayName("同一订单行不能被两个任务同时占用")
    void activeOccupationRejectsSecondTask() {
        var order = order("c2", "8.0000");
        Long itemId = order.item();
        as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("c2a", List.of(itemId), sorterA), key("c2a")));
        expectCode(() -> as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("c2b", List.of(itemId), sorterB), key("c2b"))), 41123);
    }

    @Test
    @DisplayName("取消任务释放占用：同一订单行可以重新建单，历史行仍可查")
    void cancelReleasesOccupationForResort() {
        var order = order("c3", "8.0000");
        Long itemId = order.item();
        Long first = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("c3a", List.of(itemId), sorterA), key("c3a"))).getTask().getId();

        as(lead, QUEUE_MANAGER, () -> {
            sorting.cancel(first, action(currentVersion(first), "未开工，退回队列"));
            return null;
        });
        assertThat(occupationOfTask(first)).isEqualTo("RELEASED");

        var second = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("c3b", List.of(itemId), sorterB), key("c3b")));
        assertThat(second.getItems()).hasSize(1);
        assertThat(second.getTask().getAssigneeEmployeeId()).isEqualTo(sorterB);
        assertThat(tasksOccupying(itemId)).as("历史 RELEASED 行保留，活动占用只有一条").isEqualTo(1);
        assertThat(detailItemCount(first)).as("取消的任务仍能看到自己那条明细").isEqualTo(1);
    }

    @Test
    @DisplayName("未确认订单的行与不存在的明细 id 都不能进分拣")
    void createRejectsUnsortableLines() {
        Long missing = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 900000 FROM sales_order_item", Long.class);
        expectCode(() -> as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("c3", List.of(missing), sorterA), key("c3"))), 41124);

        var pending = order("c4", "5.0000");
        jdbc.update("UPDATE sales_order SET status = 'PENDING', confirmed_at = NULL WHERE id = ?", pending.order());
        evictMybatisCache();
        expectCode(() -> as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("c5", List.of(pending.item()), sorterA), key("c5"))), 41124);
    }

    @Test
    @DisplayName("整条分拣链不写库存：余额、流水与出库单一行都不动")
    void sortingChainTouchesNoInventoryFacts() {
        var order = order("c6", "6.0000");
        int balances = count("SELECT count(*) FROM inventory_balance");
        int movements = count("SELECT count(*) FROM inventory_movement");
        int outbounds = count("SELECT count(*) FROM inventory_outbound");

        Long taskId = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("c6", List.of(order.item()), sorterA), key("c6"))).getTask().getId();
        finish(taskId);

        assertThat(count("SELECT count(*) FROM inventory_balance")).isEqualTo(balances);
        assertThat(count("SELECT count(*) FROM inventory_movement")).isEqualTo(movements);
        assertThat(count("SELECT count(*) FROM inventory_outbound")).isEqualTo(outbounds);
    }

    // ------------------------------------------------------------------
    // 录入与状态机
    // ------------------------------------------------------------------

    @Test
    @DisplayName("录入首行即进入 SORTING；未全处理不能完成，全处理后可以")
    void entryStartsSortingAndCompletionNeedsEveryActiveLine() {
        var a = order("e1", "5.0000");
        var b = order("e2", "7.0000");
        var detail = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("e", List.of(a.item(), b.item()), sorterA), key("e")));
        Long taskId = detail.getTask().getId();
        assertThat(taskStatus(taskId)).isEqualTo("PENDING");

        completeLine(taskId, detail.getItems().getFirst(), "5.0000", "NORMAL", null);
        assertThat(taskStatus(taskId)).as("首次录入把任务推到 SORTING").isEqualTo("SORTING");
        assertThat(startedAt(taskId)).isNotNull();
        expectCode(() -> doAs(sorterA, SORTER,
                () -> sorting.complete(taskId, action(currentVersion(taskId), null))), 41125);

        completeLine(taskId, detail.getItems().get(1), "4.0000", "SHORT", "当日到货不足");
        as(sorterA, SORTER, () -> {
            sorting.complete(taskId, action(currentVersion(taskId), null));
            return null;
        });
        assertThat(taskStatus(taskId)).isEqualTo("COMPLETED");
        assertThat(completedAt(taskId)).isNotNull();
    }

    @Test
    @DisplayName("缺货与少拣只落分拣结果，订单行的实发量与结算额一律不变")
    void shortageNeverMutatesOrderFacts() {
        var order = order("s1", "9.0000");
        BigDecimal beforeSettlement = settlementLineAmount(order);
        var detail = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("s1", List.of(order.item()), sorterA), key("s1")));

        completeLine(detail.getTask().getId(), detail.getItems().getFirst(), "0.0000", "OUT_OF_STOCK", "供应商未到货");

        assertThat(itemResultOfTask(detail.getTask().getId())).isEqualTo("OUT_OF_STOCK");
        assertThat(actualQuantity(order)).as("缺货不改订单实发量").isEqualByComparingTo("9.0000");
        assertThat(settlementLineAmount(order)).isEqualByComparingTo(beforeSettlement);
    }

    @Test
    @DisplayName("明细不属于本任务、版本过期、差异不写原因都被拒")
    void entryGuardsMembershipVersionAndReason() {
        var mine = order("g1", "4.0000");
        var detail = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("g1", List.of(mine.item()), sorterA), key("g1")));
        Long taskId = detail.getTask().getId();
        var line = detail.getItems().getFirst();
        Long foreignItemId = order("g2", "4.0000").item();

        expectCode(() -> doAs(sorterA, SORTER, () -> sorting.enter(taskId,
                entryForm(entryItem(foreignItemId, 0, "4.0000", "SHORT", "别人的行")))), 41122);
        expectCode(() -> doAs(sorterA, SORTER, () -> sorting.enter(taskId,
                entryForm(entryItem(line.getId(), 99, "4.0000", "NORMAL", null)))), 40921);
        expectCode(() -> doAs(sorterA, SORTER, () -> sorting.enter(taskId,
                entryForm(entryItem(line.getId(), line.getVersion(), "5.0000", "OVER", "   ")))), 40000);
        assertThat(itemResultOfTask(taskId)).as("三次被拒的录入都不能留下结果").isNull();
    }

    @Test
    @DisplayName("已取消不能再录入；已完成只能重开，且重开保留已录内容")
    void cancelledTaskIsFrozenAndCompletedTaskReopensWithValues() {
        var order = order("r1", "3.0000");
        Long cancelled = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("r1", List.of(order.item()), sorterA), key("r1"))).getTask().getId();
        as(lead, QUEUE_MANAGER, () -> {
            sorting.cancel(cancelled, action(currentVersion(cancelled), "任务下错仓库"));
            return null;
        });
        Long frozenItem = itemIdOfTask(cancelled);
        expectCode(() -> doAs(sorterA, SORTER,
                () -> sorting.enter(cancelled, entryForm(entryItem(frozenItem, 0, "1.0000", "NORMAL", null)))), 41121);
        expectCode(() -> doAs(lead, QUEUE_MANAGER,
                () -> sorting.reopen(cancelled, action(currentVersion(cancelled), "已取消无需重开"))), 41121);

        var detail = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("r2", List.of(order.item()), sorterA), key("r2")));
        Long taskId = detail.getTask().getId();
        completeLine(taskId, detail.getItems().getFirst(), "2.0000", "SHORT", "缺 1kg");
        finish(taskId);
        expectCode(() -> doAs(lead, QUEUE_MANAGER,
                () -> sorting.cancel(taskId, action(currentVersion(taskId), "已完成不能取消"))), 41121);

        as(lead, QUEUE_MANAGER, () -> {
            sorting.reopen(taskId, action(currentVersion(taskId), "客户临时改量，需要重分拣"));
            return null;
        });
        var kept = as(sorterA, SORTER, () -> queries.detail(taskId)).getItems().getFirst();
        assertThat(taskStatus(taskId)).isEqualTo("SORTING");
        assertThat(kept.getSortedQuantity()).as("重开清空内容会破坏审计，必须保留").isEqualByComparingTo("2.0000");
        assertThat(kept.getResult()).isEqualTo("SHORT");
        assertThat(completedAt(taskId)).as("曾完成的时间要留下来").isNotNull();
    }

    // ------------------------------------------------------------------
    // 指派与范围
    // ------------------------------------------------------------------

    @Test
    @DisplayName("改派保留已录内容；未指派任务只有队列管理者看得见")
    void assignKeepsEnteredFactsAndReassignsVisibility() {
        var order = order("a1", "4.0000");
        Long taskId = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("a1", List.of(order.item()), null), key("a1"))).getTask().getId();
        assertThat(as(lead, QUEUE_MANAGER, () -> queries.detail(taskId)).getTask().getAssigneeEmployeeId()).isNull();
        assertThatThrownBy(() -> as(sorterA, SORTER, () -> queries.detail(taskId)))
                .isInstanceOf(BusinessException.class).hasMessage(UserErrorCode.NO_PERMISSION.getMsg());

        var form = new SortingAssignForm();
        form.setAssigneeEmployeeId(sorterA);
        form.setVersion(currentVersion(taskId));
        form.setReason("按排班指派");
        as(lead, QUEUE_MANAGER, () -> {
            sorting.assign(taskId, form);
            return null;
        });
        assertThat(as(sorterA, SORTER, () -> queries.detail(taskId)).getTask().getAssigneeEmployeeId())
                .isEqualTo(sorterA);

        completeLine(taskId, itemIdOfTask(taskId), "4.0000", "NORMAL", null);
        form.setAssigneeEmployeeId(sorterB);
        form.setVersion(currentVersion(taskId));
        as(lead, QUEUE_MANAGER, () -> {
            sorting.assign(taskId, form);
            return null;
        });
        var after = as(sorterB, SORTER, () -> queries.detail(taskId)).getItems().getFirst();
        assertThat(after.getSortedQuantity()).as("改派不换已录入内容").isEqualByComparingTo("4.0000");
        assertThatThrownBy(() -> as(sorterA, SORTER, () -> queries.detail(taskId)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("范围是「授权仓 ∩ 可见指派人」：分拣员只看派给自己的，管理者看全部")
    void visibilityIntersectsWarehouseAndAssignee() {
        var mine = order("v1", "2.0000");
        var theirs = order("v2", "2.0000");
        var free = order("v3", "2.0000");
        Long myTask = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("v1", List.of(mine.item()), sorterA), key("v1"))).getTask().getId();
        Long theirTask = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("v2", List.of(theirs.item()), sorterB), key("v2"))).getTask().getId();
        Long freeTask = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("v3", List.of(free.item()), null), key("v3"))).getTask().getId();

        assertThat(as(sorterA, SORTER, () -> visibleTaskIds())).containsExactly(myTask);
        assertThatThrownBy(() -> as(sorterA, SORTER, () -> queries.detail(theirTask)))
                .isInstanceOf(BusinessException.class).hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
        assertThatThrownBy(() -> as(sorterA, SORTER, () -> queries.detail(freeTask)))
                .isInstanceOf(BusinessException.class).hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
        assertThat(as(lead, QUEUE_MANAGER, () -> visibleTaskIds())).contains(myTask, theirTask, freeTask);

        // 未指派任务没有人是「本人」，因此谁也录不进去。
        assertThatThrownBy(() -> doAs(sorterA, SORTER, () -> sorting.enter(freeTask,
                entryForm(entryItem(itemIdOfTask(freeTask), 0, "1.0000", "NORMAL", null)))))
                .isInstanceOf(BusinessException.class).hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
    }

    @Test
    @DisplayName("没有任何授权仓：列表与汇总都是形状完整的空结果，建单被拒")
    void emptyWarehouseScopeFailsClosed() {
        Long outsider = newEmployee("N0");
        var page = as(outsider, Set.of(QUERY_PERM), () -> queries.query(listForm()));
        assertThat(page.getTotal()).isZero();
        assertThat(page.getEmptyFlag()).isTrue();
        var summary = as(outsider, Set.of(SUMMARY_PERM), () -> queries.summary(summaryForm()));
        assertThat(summary.getTotal()).isZero();
        var order = order("n1", "1.0000");
        assertThatThrownBy(() -> as(outsider, QUEUE_MANAGER,
                () -> sorting.create(createForm("n1", List.of(order.item()), outsider), key("n1"))))
                .isInstanceOf(BusinessException.class).hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
    }

    @Test
    @DisplayName("只有受指派人本人能录入与完成；队列管理者不能代干")
    void onlyAssigneeMayWork() {
        var order = order("w1", "2.0000");
        var detail = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("w1", List.of(order.item()), sorterA), key("w1")));
        Long taskId = detail.getTask().getId();
        var line = detail.getItems().getFirst();
        SortingEntryForm entry = entryForm(entryItem(line.getId(), line.getVersion(), "2.0000", "NORMAL", null));

        assertThatThrownBy(() -> doAs(lead, QUEUE_MANAGER, () -> sorting.enter(taskId, entry)))
                .isInstanceOf(BusinessException.class).hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
        assertThatThrownBy(() -> doAs(sorterB, SORTER, () -> sorting.enter(taskId, entry)))
                .isInstanceOf(BusinessException.class).hasMessage(UserErrorCode.NO_PERMISSION.getMsg());

        completeLine(taskId, line, "2.0000", "NORMAL", null);
        assertThatThrownBy(() -> doAs(sorterB, SORTER,
                () -> sorting.complete(taskId, action(currentVersion(taskId), null))))
                .isInstanceOf(BusinessException.class).hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
    }

    /**
     * break-glass：超管位绕过数据范围是 P0 定下的不变量，「受指派人」这一维是直接比员工 id
     * 而不是范围值对象，因此必须同等放行 —— 否则同一个账号会「仓库看得见、人看不见」。
     * 反过来它也**不是权限证据**：正向能力一律由上面的角色账号取证，这里只钉口径不放宽。
     */
    @Test
    @DisplayName("未指派任务对分拣员不可操作，但超管按 break-glass 可以代为处理")
    void administratorBreakGlassCoversAssigneeDimension() {
        var order = order("bg1", "2.0000");
        // 夹具默认身份是 administratorFlag=true 的员工 1，且不经过任何权限 mock。
        Long taskId = sorting.create(createForm("bg1", List.of(order.item()), null), key("bg1")).getTask().getId();
        assertThat(jdbc.queryForObject("SELECT assignee_employee_id FROM sorting_task WHERE id = ?",
                Long.class, taskId)).as("未指派").isNull();

        // 录入与完成都用夹具默认的超管身份，不经过任何权限 mock。
        var line = queries.detail(taskId).getItems().getFirst();
        sorting.enter(taskId, entryForm(entryItem(line.getId(), line.getVersion(),
                line.getPlannedQuantitySnapshot().toPlainString(), "NORMAL", null)));
        sorting.complete(taskId, action(currentVersion(taskId), null));
        assertThat(taskStatus(taskId)).isEqualTo("COMPLETED");

        var order2 = order("bg2", "3.0000");
        Long other = sorting.create(createForm("bg2", List.of(order2.item()), null), key("bg2")).getTask().getId();
        assertThatThrownBy(() -> doAs(sorterA, SORTER, () -> queries.detail(other)))
                .as("同一张未指派任务，分拣员侧仍然越不到")
                .isInstanceOf(BusinessException.class).hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
    }

    // ------------------------------------------------------------------
    // 两个视角同一套事实、打印与资格
    // ------------------------------------------------------------------

    @Test
    @DisplayName("按商品汇总与按订单视角取自同一批明细行，且不跨单位求和")
    void summaryAndOrderViewShareOneFactSet() {
        Long skuId = newOnShelfSku("m0");
        var a = confirmedSalesOrder(newCustomer(), skuId, "3.0000", "3.0000");
        var b = confirmedSalesOrder(newCustomer(), skuId, "4.0000", "4.0000");
        Long taskId = as(lead, QUEUE_MANAGER, () -> sorting.create(
                createForm("m", List.of(confirmedSalesOrderItemId(a), confirmedSalesOrderItemId(b)), sorterA),
                key("m"))).getTask().getId();
        var detail = as(sorterA, SORTER, () -> queries.detail(taskId));
        completeLine(taskId, detail.getItems().getFirst(), "3.0000", "NORMAL", null);

        var page = as(sorterA, Set.of(SUMMARY_PERM), () -> queries.summary(summaryForm()));
        var row = page.getList().stream().filter(v -> v.getSkuId().equals(skuId)).findFirst().orElseThrow();
        assertThat(row.getLineCount()).as("两条订单行 = 汇总里的一行两条明细").isEqualTo(2);
        assertThat(row.getOrderCount()).isEqualTo(2);
        assertThat(row.getTaskCount()).isEqualTo(1);
        assertThat(row.getUnprocessedCount()).isEqualTo(1);
        assertThat(row.getPlannedQuantity()).as("同单位才可相加：3 + 4").isEqualByComparingTo("7.0000");
        assertThat(row.getSortedQuantity()).as("只有已录入的行进汇总量").isEqualByComparingTo("3.0000");
    }

    @Test
    @DisplayName("打印只计次：状态、库存与版本都不动，同键重放只计一次")
    void printOnlyCountsAndReplayCountsOnce() {
        var order = order("p1", "2.0000");
        Long taskId = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("p1", List.of(order.item()), sorterA), key("p1"))).getTask().getId();
        finish(taskId);
        int movements = count("SELECT count(*) FROM inventory_movement");
        Integer version = currentVersion(taskId);

        var first = as(sorterA, Set.of(PRINT_PERM),
                () -> sorting.print(taskId, action(version, null), key("p1")));
        var replay = as(sorterA, Set.of(PRINT_PERM),
                () -> sorting.print(taskId, action(version, null), key("p1")));
        var second = as(sorterA, Set.of(PRINT_PERM),
                () -> sorting.print(taskId, action(version, null), key("p2")));

        assertThat(first.getPrintCount()).isEqualTo(1);
        assertThat(replay.getPrintCount()).as("同一幂等键重放只计一次").isEqualTo(1);
        assertThat(second.getPrintCount()).isEqualTo(2);
        assertThat(taskStatus(taskId)).isEqualTo("COMPLETED");
        assertThat(currentVersion(taskId)).as("打印不改版本，否则会把别人的编辑顶成假冲突").isEqualTo(version);
        assertThat(count("SELECT count(*) FROM inventory_movement")).isEqualTo(movements);
    }

    @Test
    @DisplayName("待分拣任务不能打印；预览不计次")
    void pendingTaskIsNotPrintableAndPreviewDoesNotCount() {
        var order = order("p3", "2.0000");
        Long taskId = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("p3", List.of(order.item()), sorterA), key("p3"))).getTask().getId();
        expectCode(() -> as(sorterA, Set.of(PRINT_PERM), () -> queries.printPreview(taskId)), 41121);
        expectCode(() -> as(sorterA, Set.of(PRINT_PERM),
                () -> sorting.print(taskId, action(currentVersion(taskId), null), key("p3"))), 41121);
        assertThat(printCount(taskId)).isZero();

        completeLine(taskId, itemIdOfTask(taskId), "2.0000", "NORMAL", null);
        var preview = as(sorterA, Set.of(PRINT_PERM), () -> queries.printPreview(taskId));
        assertThat(preview.getItems()).hasSize(1);
        assertThat(preview.getTaskNo()).startsWith("SRT");
        assertThat(printCount(taskId)).as("预览不占用幂等键也不计次").isZero();
    }

    @Test
    @DisplayName("订单只有全部有效行被已完成任务覆盖才可配送，重开后立刻不合格")
    void deliveryEligibilityRequiresCompletedCoverageAndBreaksOnReopen() {
        var a = order("d1", "3.0000");
        var b = order("d2", "4.0000");
        assertThat(eligible(a.order())).as("未分拣的已确认订单不合格").isFalse();

        Long taskA = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("d1", List.of(a.item()), sorterA), key("d1"))).getTask().getId();
        Long taskB = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("d2", List.of(b.item()), sorterA), key("d2"))).getTask().getId();
        finish(taskA);
        assertThat(eligible(a.order())).as("任务完成即合格").isTrue();
        assertThat(eligible(b.order())).as("另一张订单不受影响").isFalse();
        finish(taskB);
        assertThat(eligible(b.order())).isTrue();

        as(lead, QUEUE_MANAGER, () -> {
            sorting.reopen(taskA, action(currentVersion(taskA), "复检发现重量不实"));
            return null;
        });
        assertThat(eligible(a.order())).as("重开只改任务状态就能让订单不合格").isFalse();
        assertThat(eligible(b.order())).isTrue();
    }

    @Test
    @DisplayName("取消已完成任务的订单会掉出候选，重新建单完成后再次合格")
    void cancelThenResortRestoresEligibility() {
        var order = order("d3", "5.0000");
        Long taskId = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("d3", List.of(order.item()), sorterA), key("d3"))).getTask().getId();
        finish(taskId);
        assertThat(eligible(order.order())).isTrue();

        as(lead, QUEUE_MANAGER, () -> {
            sorting.reopen(taskId, action(currentVersion(taskId), "需要重分拣"));
            return null;
        });
        as(lead, QUEUE_MANAGER, () -> {
            sorting.cancel(taskId, action(currentVersion(taskId), "整单作废重做"));
            return null;
        });
        assertThat(eligible(order.order())).isFalse();

        Long again = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("d4", List.of(order.item()), sorterA), key("d4"))).getTask().getId();
        finish(again);
        assertThat(eligible(order.order())).as("重做完成后恢复合格：占用位随任务取消而释放").isTrue();
    }

    // ------------------------------------------------------------------
    // 库内约束：V60 的 CHECK 与部分唯一索引必须独立成立
    // ------------------------------------------------------------------

    @Test
    @DisplayName("V60 约束独立生效：结果与量成对、差异必须有原因、活动占用唯一")
    void databaseConstraintsHoldOnTheirOwn() {
        var order = order("k1", "2.0000");
        Long taskId = as(lead, QUEUE_MANAGER,
                () -> sorting.create(createForm("k1", List.of(order.item()), sorterA), key("k1"))).getTask().getId();
        Long itemId = itemIdOfTask(taskId);

        expectSqlFailure("UPDATE sorting_task_item SET sorted_quantity = 1 WHERE id = ?", itemId);
        expectSqlFailure("UPDATE sorting_task_item SET result = 'SHORT' WHERE id = ?", itemId);
        expectSqlFailure("UPDATE sorting_task_item SET sorted_quantity = 1, result = 'OVER', reason = '  ' WHERE id = ?",
                itemId);
        expectSqlFailure("UPDATE sorting_task_item SET occupation_status = 'BOGUS' WHERE id = ?", itemId);
        // 同一订单行第二条活动占用：绕过服务层直接插，也必须被部分唯一索引拦住。
        expectSqlFailure("INSERT INTO sorting_task_item (task_id, sales_order_id, sales_order_item_id,"
                        + " order_no_snapshot, customer_id, customer_name_snapshot, spu_id, sku_id,"
                        + " product_name_snapshot, sale_unit_snapshot, product_type_snapshot,"
                        + " planned_quantity_snapshot, occupation_status)"
                        + " SELECT t.id, i.order_id, i.id, 'X', 1, 'X', i.spu_id, i.sku_id, 'X', 'kg',"
                        + " 'NON_STANDARD', 1, 'ACTIVE' FROM sorting_task t, sales_order_item i"
                        + " WHERE t.id = ? AND i.id = ?", taskId, order.item());
        // 正常成对且带原因的写法必须通过，否则上面几条只是「什么都写不进去」。
        jdbc.update("UPDATE sorting_task_item SET sorted_quantity = 1, result = 'SHORT', reason = '少拣' WHERE id = ?",
                itemId);
        assertThat(jdbc.queryForObject("SELECT reason FROM sorting_task_item WHERE id = ?", String.class, itemId))
                .isEqualTo("少拣");
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private record OrderLine(Long order, Long item) {
    }

    private OrderLine order(String tag, String quantity) {
        Long skuId = newOnShelfSku(tag);
        Long customerId = newCustomer();
        Long orderId = confirmedSalesOrder(customerId, skuId, quantity, quantity);
        return new OrderLine(orderId, confirmedSalesOrderItemId(orderId));
    }

    private SortingTaskCreateForm createForm(String tag, List<Long> itemIds, Long assignee) {
        var form = new SortingTaskCreateForm();
        form.setWarehouseId(warehouse);
        form.setAssigneeEmployeeId(assignee);
        form.setSalesOrderItemIds(new ArrayList<>(itemIds));
        form.setRemark("P1 IT " + tag);
        return form;
    }

    private SortingActionForm action(Integer version, String reason) {
        var form = new SortingActionForm();
        form.setVersion(version);
        form.setReason(reason);
        return form;
    }

    private SortingEntryItemForm entryItem(Long id, Integer version, String quantity, String result, String reason) {
        var item = new SortingEntryItemForm();
        item.setId(id);
        item.setVersion(version);
        item.setSortedQuantity(new BigDecimal(quantity));
        item.setResult(result);
        item.setReason(reason);
        return item;
    }

    private SortingEntryForm entryForm(SortingEntryItemForm... items) {
        var form = new SortingEntryForm();
        form.setItems(List.of(items));
        return form;
    }

    /**
     * 按库里当前版本录入，让「这一步成不成立」不被版本噪声干扰。
     */
    private void completeLine(Long taskId, SortingTaskItemVO line, String quantity, String result, String reason) {
        doAs(sorterA, SORTER, () -> sorting.enter(taskId, entryForm(
                entryItem(line.getId(), itemVersion(line.getId()), quantity, result, reason))));
    }

    private void completeLine(Long taskId, Long itemId, String quantity, String result, String reason) {
        doAs(sorterA, SORTER, () -> sorting.enter(taskId, entryForm(
                entryItem(itemId, itemVersion(itemId), quantity, result, reason))));
    }

    /**
     * 把任务做到 COMPLETED：逐行按快照量正常收口，再带当前版本完成。
     */
    private void finish(Long taskId) {
        var detail = as(sorterA, SORTER, () -> queries.detail(taskId));
        for (var line : detail.getItems()) {
            if (line.getResult() != null) continue;
            completeLine(taskId, line, line.getPlannedQuantitySnapshot().toPlainString(), "NORMAL", null);
        }
        as(sorterA, SORTER, () -> {
            sorting.complete(taskId, action(currentVersion(taskId), null));
            return null;
        });
    }

    private <T> T as(Long employeeId, Set<String> permissions, Supplier<T> body) {
        loginAs(employeeId);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            permissions.forEach(p -> stp.when(() -> StpUtil.hasPermission(p)).thenReturn(true));
            return body.get();
        }
    }

    /**
     * 无返回值动作的那一侧：服务方法都是 void，包一层才不会把脚手架异常当成业务结果。
     */
    private void doAs(Long employeeId, Set<String> permissions, Runnable body) {
        as(employeeId, permissions, () -> {
            body.run();
            return null;
        });
    }

    private void loginAs(Long employeeId) {
        var employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setLoginName("P1-" + employeeId);
        employee.setActualName("P1 分拣测试员工");
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
                "分拣" + suffix);
        Long employeeId = jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
        assertThat(jdbc.queryForObject(
                "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                .as("权限证据必须来自非超管账号").isFalse();
        return employeeId;
    }

    private void grantWarehouseScope(Long employeeId) {
        jdbc.update("INSERT INTO employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                employeeId, warehouse);
    }

    // ---- 回读 ----

    private List<Long> visibleTaskIds() {
        return queries.query(listForm()).getList().stream().map(v -> v.getId()).toList();
    }

    private SortingTaskQueryForm listForm() {
        var form = new SortingTaskQueryForm();
        form.setPageNum(1L);
        form.setPageSize(100L);
        return form;
    }

    private SortingSummaryQueryForm summaryForm() {
        var form = new SortingSummaryQueryForm();
        form.setPageNum(1L);
        form.setPageSize(100L);
        return form;
    }

    private Integer currentVersion(Long taskId) {
        return jdbc.queryForObject("SELECT version FROM sorting_task WHERE id = ?", Integer.class, taskId);
    }

    private Integer itemVersion(Long itemId) {
        return jdbc.queryForObject("SELECT version FROM sorting_task_item WHERE id = ?", Integer.class, itemId);
    }

    private String taskStatus(Long taskId) {
        return jdbc.queryForObject("SELECT status FROM sorting_task WHERE id = ?", String.class, taskId);
    }

    private Long itemIdOfTask(Long taskId) {
        return jdbc.queryForObject("SELECT id FROM sorting_task_item WHERE task_id = ? ORDER BY id LIMIT 1",
                Long.class, taskId);
    }

    private String occupationOfTask(Long taskId) {
        return jdbc.queryForObject(
                "SELECT occupation_status FROM sorting_task_item WHERE task_id = ? ORDER BY id LIMIT 1",
                String.class, taskId);
    }

    private String itemResultOfTask(Long taskId) {
        return jdbc.queryForObject("SELECT result FROM sorting_task_item WHERE task_id = ? ORDER BY id LIMIT 1",
                String.class, taskId);
    }

    private OffsetDateTime startedAt(Long taskId) {
        return jdbc.queryForObject("SELECT started_at FROM sorting_task WHERE id = ?", OffsetDateTime.class, taskId);
    }

    private OffsetDateTime completedAt(Long taskId) {
        return jdbc.queryForObject("SELECT completed_at FROM sorting_task WHERE id = ?", OffsetDateTime.class, taskId);
    }

    private int printCount(Long taskId) {
        return jdbc.queryForObject("SELECT print_count FROM sorting_task WHERE id = ?", Integer.class, taskId);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    /**
     * 一条订单行当前的活动占用数。按订单行数而不是按整张表数：
     * 并发类用例会在同一张表上提交真实行，全表计数只能测出「谁先跑」。
     */
    private int tasksOccupying(Long orderItemId) {
        return jdbc.queryForObject("SELECT count(*) FROM sorting_task_item WHERE sales_order_item_id = ?"
                + " AND occupation_status = 'ACTIVE' AND deleted = FALSE", Integer.class, orderItemId);
    }

    private int detailItemCount(Long taskId) {
        return as(sorterA, SORTER, () -> queries.detail(taskId)).getItems().size();
    }

    private BigDecimal actualQuantity(OrderLine line) {
        return jdbc.queryForObject("SELECT actual_quantity FROM sales_order_item WHERE id = ?",
                BigDecimal.class, line.item());
    }

    private BigDecimal settlementLineAmount(OrderLine line) {
        return jdbc.queryForObject("SELECT settlement_line_amount FROM sales_order_item WHERE id = ?",
                BigDecimal.class, line.item());
    }

    private boolean eligible(Long orderId) {
        return eligibility.eligible(orders.selectById(orderId));
    }

    /**
     * 幂等键逐次唯一：{@code claim} 按「操作人 + 作用域 + 键」定位，
     * 复用常量会让同一用例里第二次调用被当成重放而不是新请求。
     */
    private String key(String tag) {
        return prefix + ":sorting:" + tag;
    }
}
