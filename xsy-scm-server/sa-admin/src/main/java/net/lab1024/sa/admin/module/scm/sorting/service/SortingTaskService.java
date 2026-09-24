package net.lab1024.sa.admin.module.scm.sorting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.util.ScmDocumentNumbers;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryOutboundItemDao;
import net.lab1024.sa.admin.module.scm.order.service.OrderIdempotencyService;
import net.lab1024.sa.admin.module.scm.sorting.dao.SortingQueryDao;
import net.lab1024.sa.admin.module.scm.sorting.dao.SortingTaskDao;
import net.lab1024.sa.admin.module.scm.sorting.dao.SortingTaskItemDao;
import net.lab1024.sa.admin.module.scm.sorting.domain.dto.SortingOrderLineSnapshot;
import net.lab1024.sa.admin.module.scm.sorting.domain.entity.SortingRecord;
import net.lab1024.sa.admin.module.scm.sorting.domain.entity.SortingTaskEntity;
import net.lab1024.sa.admin.module.scm.sorting.domain.entity.SortingTaskItemEntity;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingActionForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingAssignForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingEntryItemForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.form.SortingTaskCreateForm;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingPrintResultVO;
import net.lab1024.sa.admin.module.scm.sorting.domain.vo.SortingTaskDetailVO;
import net.lab1024.sa.admin.module.scm.sorting.support.SortingAccess;
import net.lab1024.sa.admin.module.scm.warehouse.dao.WarehouseDao;
import net.lab1024.sa.admin.module.system.employee.dao.EmployeeDao;
import net.lab1024.sa.admin.module.system.employee.domain.entity.EmployeeEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingConstant.CANCELLED;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingConstant.COMPLETED;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingConstant.NORMAL;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingConstant.OCCUPY_ACTIVE;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingConstant.PENDING;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingConstant.PRINTABLE;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingConstant.SORTING;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingConstant.TASK_NO_PREFIX;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingConstant.WORKING;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingErrorCode.ASSIGNEE_INVALID;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingErrorCode.ITEM_NOT_IN_TASK;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingErrorCode.ORDER_LINE_TAKEN;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingErrorCode.ORDER_NOT_SORTABLE;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingErrorCode.OUTBOUND_EXISTS;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingErrorCode.RESULT_INCOMPLETE;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingErrorCode.STATE_INVALID;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingErrorCode.TASK_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.sorting.constant.SortingErrorCode.WAREHOUSE_INVALID;

/**
 * 分拣任务的全部写侧动作。<b>任务行是聚合锁</b>：每个动作先锁 {@code sorting_task}，
 * 再动明细，因此「任务状态」与「明细占用位」不会各自漂移——后者是部分唯一索引成立的前提。
 *
 * <p>这个类不写订单、库存与配送：不回写 {@code sales_order_item} 的实发量与结算金额，
 * 不写余额与流水，不创建出库单，也不动预留触发点（裁决第 1、3、6 条）。
 * 取消与重开的理由只进通用 {@code t_operate_log} 的请求参数，不另建分拣审计表（第 12 条）。
 *
 * <p>唯一的例外是<b>读</b>库存：重开前要查这些订单行是否已经过发车真实出库
 * （{@code reopen} 与 P2 裁决第 11 条）。读侧不产生任何库存事实，方向也是单向的 ——
 * 库存域不认识分拣，分拣只在守卫上读它。
 */
@Service
@RequiredArgsConstructor
public class SortingTaskService {

    /**
     * 一次建单并入的订单行上限，避免生成上千行的巨型任务。
     */
    private static final int MAX_LINES_PER_TASK = 500;

    private final InventoryOutboundItemDao outboundItems;
    private final SortingTaskDao tasks;
    private final SortingTaskItemDao itemRows;
    private final SortingQueryDao queries;
    private final SortingAccess access;
    private final WarehouseDao warehouses;
    private final EmployeeDao employees;
    private final OrderIdempotencyService idempotency;
    private final SortingQueryService queryService;

    /**
     * 建单并指派。幂等键挡住「同一请求重发生成第二套业务事实」；
     * 不同请求抢同一订单行由部分唯一索引兜底，报可读的占用冲突。
     */
    @Transactional(rollbackFor = Exception.class)
    public SortingTaskDetailVO create(SortingTaskCreateForm form, String key) {
        var claim = idempotency.claim("SORTING_TASK_CREATE", key, form);
        if (claim.replay()) return idempotency.replay(claim, SortingTaskDetailVO.class);
        access.requireWarehouse(form.getWarehouseId());
        var warehouse = warehouses.selectById(form.getWarehouseId());
        if (warehouse == null || !"ENABLED".equals(warehouse.getStatus()))
            throw new ScmBusinessException(WAREHOUSE_INVALID);
        requireUsableAssignee(form.getAssigneeEmployeeId());
        var lines = sortableLines(form.getSalesOrderItemIds());

        var task = new SortingTaskEntity();
        task.setTaskNo(ScmDocumentNumbers.format(TASK_NO_PREFIX, queries.nextNumber()));
        task.setWarehouseId(warehouse.getId());
        task.setWarehouseNameSnapshot(warehouse.getName());
        task.setAssigneeEmployeeId(form.getAssigneeEmployeeId());
        task.setStatus(PENDING);
        task.setRemark(trimToNull(form.getRemark()));
        task.setPrintCount(0);
        stamp(task, true);
        tasks.insert(task);

        try {
            for (var line : lines) itemRows.insert(newItem(task.getId(), line));
        } catch (DuplicateKeyException taken) {
            throw new ScmBusinessException(ORDER_LINE_TAKEN);
        }
        var result = queryService.detail(task.getId());
        idempotency.complete(claim, "SORTING_TASK", task.getId(), result);
        return result;
    }

    /**
     * 指派 / 改派：换人不换已录入内容；已完成与已取消的任务不再改派。
     */
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long id, SortingAssignForm form) {
        ScmDataScopeContext scope = access.scope();
        var task = lock(id);
        access.requireQueueManager(scope, task);
        requireVersion(task, form.getVersion());
        if (!WORKING.contains(task.getStatus())) throw new ScmBusinessException(STATE_INVALID);
        requireUsableAssignee(form.getAssigneeEmployeeId());
        task.setAssigneeEmployeeId(form.getAssigneeEmployeeId());
        save(task);
    }

    /**
     * 批量录入分拣结果。允许一次只处理任务里的部分行（边称边录是常态），但每条提交行都必须
     * 属于本任务且带它自己读到的版本；首次录入把任务从 {@code PENDING} 推到 {@code SORTING}。
     */
    @Transactional(rollbackFor = Exception.class)
    public void enter(Long id, SortingEntryForm form) {
        ScmDataScopeContext scope = access.scope();
        var task = lock(id);
        access.requireAssignee(scope, task);
        if (!WORKING.contains(task.getStatus())) throw new ScmBusinessException(STATE_INVALID);
        var rows = activeItems(id);
        for (var entry : form.getItems()) {
            var row = rows.get(entry.getId());
            if (row == null) throw new ScmBusinessException(ITEM_NOT_IN_TASK);
            if (!Objects.equals(row.getVersion(), entry.getVersion())) throw new ScmBusinessException(VERSION_CONFLICT);
            if (!NORMAL.equals(entry.getResult()) && trimToNull(entry.getReason()) == null)
                throw new ScmBusinessException(VALIDATION_ERROR);
            row.setSortedQuantity(entry.getSortedQuantity());
            row.setResult(entry.getResult());
            row.setReason(trimToNull(entry.getReason()));
            row.setSortedBy(ScmOperator.current());
            row.setSortedAt(OffsetDateTime.now());
            stamp(row, false);
            // 同一批里重复提交同一行时，第二次带的是已被自己改掉的旧版本，在这里就断掉。
            if (itemRows.updateById(row) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
        }
        if (PENDING.equals(task.getStatus())) {
            task.setStatus(SORTING);
            if (task.getStartedAt() == null) task.setStartedAt(OffsetDateTime.now());
            save(task);
        }
    }

    /**
     * 完成：硬前置是任务内每条活动明细都已有结果（裁决补充第 17 条）。
     * 一张订单要分多次完成时靠「剩余有效行另建新任务」实现，不靠带洞完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, SortingActionForm form) {
        ScmDataScopeContext scope = access.scope();
        var task = lock(id);
        access.requireAssignee(scope, task);
        requireVersion(task, form.getVersion());
        if (!WORKING.contains(task.getStatus())) throw new ScmBusinessException(STATE_INVALID);
        var rows = activeItems(id).values();
        if (rows.isEmpty() || rows.stream().anyMatch(r -> r.getResult() == null))
            throw new ScmBusinessException(RESULT_INCOMPLETE);
        task.setStatus(COMPLETED);
        task.setCompletedAt(OffsetDateTime.now());
        save(task);
    }

    /**
     * 取消：同一事务里释放该任务全部活动明细的占用位，被释放的订单行才能重新进入新任务。
     * 已完成的任务不能直接取消，必须先重开 —— 否则「完成」这一事实会被静默抹掉。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, SortingActionForm form) {
        ScmDataScopeContext scope = access.scope();
        var task = lock(id);
        access.requireQueueManager(scope, task);
        requireVersion(task, form.getVersion());
        if (!WORKING.contains(task.getStatus())) throw new ScmBusinessException(STATE_INVALID);
        requireReason(form.getReason());
        queries.releaseItems(id, ScmOperator.current());
        task.setStatus(CANCELLED);
        task.setCancelledAt(OffsetDateTime.now());
        save(task);
    }

    /**
     * 重开：已完成任务回到分拣中，已录入的量与原因**保留**继续修改（裁决补充第 18 条）。
     * 订单在配送资格上立刻不合格，靠的是资格按任务状态判定，而不是靠清数据。
     *
     * <p><b>已产生真实出库则禁止重开</b>（P1 裁决第 10 条的后半，由 P2 裁决第 11 条补回）：
     * 判据是「本任务某条明细对应的订单行上存在<b>父单为 CONFIRMED</b> 的出库行」。
     * 只认 CONFIRMED 是因为出库单的取消只在 DRAFT 可用且不发任何流水，
     * 没扣过库存的出库行不构成「货已出去」这个事实。仍然不使用「仓库 + SKU + 时间窗」近似判据。
     *
     * <p>与发车的交错：发车在线路锁内重查资格并把任务所在订单行写进出库单，
     * 因此「先重开成功 → 发车整条被拒」与「先发车成功 → 重开被本条拦住」都是自洽的串行结果；
     * 反过来若本方法早于发车提交，发车侧的资格复核会把它挡在线路之外。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reopen(Long id, SortingActionForm form) {
        ScmDataScopeContext scope = access.scope();
        var task = lock(id);
        access.requireQueueManager(scope, task);
        requireVersion(task, form.getVersion());
        if (!COMPLETED.equals(task.getStatus())) throw new ScmBusinessException(STATE_INVALID);
        requireReason(form.getReason());
        var orderLineIds = activeItems(id).values().stream()
                .map(SortingTaskItemEntity::getSalesOrderItemId)
                .filter(Objects::nonNull)
                .toList();
        if (!orderLineIds.isEmpty() && !outboundItems.listOrderLinesWithConfirmedOutbound(orderLineIds).isEmpty()) {
            throw new ScmBusinessException(OUTBOUND_EXISTS);
        }
        task.setStatus(SORTING);
        save(task);
    }

    /**
     * 正式生成打印：只登记计次、时间与操作人。
     * 打印不改状态也不写库存，并且不 bump 版本 —— 内容没变，让计次把别人的编辑顶成版本冲突
     * 是假冲突；计次累加由本方法持有的任务聚合锁串行化。
     */
    @Transactional(rollbackFor = Exception.class)
    public SortingPrintResultVO print(Long id, SortingActionForm form, String key) {
        var claim = idempotency.claim("SORTING_PRINT:" + id, key, form);
        if (claim.replay()) return idempotency.replay(claim, SortingPrintResultVO.class);
        ScmDataScopeContext scope = access.scope();
        var task = lock(id);
        access.requireVisible(scope, task);
        requireVersion(task, form.getVersion());
        if (!PRINTABLE.contains(task.getStatus())) throw new ScmBusinessException(STATE_INVALID);
        if (queries.markPrinted(id, ScmOperator.current()) != 1) throw new ScmBusinessException(STATE_INVALID);
        var result = new SortingPrintResultVO();
        result.setTaskId(task.getId());
        result.setTaskNo(task.getTaskNo());
        result.setItemCount(activeItems(id).size());
        result.setPrintCount(task.getPrintCount() + 1);
        result.setGeneratedAt(OffsetDateTime.now());
        idempotency.complete(claim, "SORTING_TASK", id, result);
        return result;
    }

    /**
     * 校验并冻结候选订单行：一次读回全部请求行，行数不等即说明有 id 根本不存在。
     */
    private List<SortingOrderLineSnapshot> sortableLines(List<Long> requested) {
        var ids = requested.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty() || ids.size() > MAX_LINES_PER_TASK) throw new ScmBusinessException(VALIDATION_ERROR);
        var found = queries.orderLines(ids);
        if (found.size() != ids.size()) throw new ScmBusinessException(ORDER_NOT_SORTABLE);
        for (var line : found) {
            if (!"CONFIRMED".equals(line.getOrderStatus()) || Boolean.TRUE.equals(line.getOrderDeleted())
                    || Boolean.TRUE.equals(line.getItemDeleted()) || line.getActualQuantity() == null)
                throw new ScmBusinessException(ORDER_NOT_SORTABLE);
        }
        return found;
    }

    private SortingTaskItemEntity newItem(Long taskId, SortingOrderLineSnapshot line) {
        var item = new SortingTaskItemEntity();
        item.setTaskId(taskId);
        item.setSalesOrderId(line.getSalesOrderId());
        item.setSalesOrderItemId(line.getSalesOrderItemId());
        item.setOrderNoSnapshot(line.getOrderNo());
        item.setCustomerId(line.getCustomerId());
        item.setCustomerNameSnapshot(line.getCustomerName());
        item.setSpuId(line.getSpuId());
        item.setSkuId(line.getSkuId());
        item.setSpuCodeSnapshot(line.getSpuCodeSnapshot());
        item.setProductNameSnapshot(line.getProductNameSnapshot());
        item.setSkuCodeSnapshot(line.getSkuCodeSnapshot());
        item.setSpecNameSnapshot(line.getSpecNameSnapshot());
        item.setSaleUnitSnapshot(line.getSaleUnitSnapshot());
        item.setProductTypeSnapshot(line.getProductTypeSnapshot());
        // 计划量 = 建单时冻结的订单行实发量；订单侧的值之后怎么变都不追溯已生成的任务。
        item.setPlannedQuantitySnapshot(line.getActualQuantity());
        item.setOccupationStatus(OCCUPY_ACTIVE);
        item.setVersion(0);
        stamp(item, true);
        return item;
    }

    /**
     * 任务当前的活动明细，按明细 id 索引。已随取消释放的行不在其中：它们不再代表待办量。
     */
    private Map<Long, SortingTaskItemEntity> activeItems(Long taskId) {
        var map = new LinkedHashMap<Long, SortingTaskItemEntity>();
        itemRows.selectList(new LambdaQueryWrapper<SortingTaskItemEntity>()
                .eq(SortingTaskItemEntity::getTaskId, taskId)
                .eq(SortingTaskItemEntity::getOccupationStatus, OCCUPY_ACTIVE)
                .orderByAsc(SortingTaskItemEntity::getId)).forEach(r -> map.put(r.getId(), r));
        return map;
    }

    private SortingTaskEntity lock(Long id) {
        var task = queries.lockTask(id);
        if (task == null) throw new ScmBusinessException(TASK_NOT_FOUND);
        return task;
    }

    /**
     * 未指派是合法状态（建单时可以后派），因此只在显式给了人时校验身份。
     */
    private void requireUsableAssignee(Long employeeId) {
        if (employeeId == null) return;
        EmployeeEntity employee = employees.selectById(employeeId);
        if (employee == null || Boolean.TRUE.equals(employee.getDeletedFlag())
                || Boolean.TRUE.equals(employee.getDisabledFlag()))
            throw new ScmBusinessException(ASSIGNEE_INVALID);
    }

    private void requireVersion(SortingTaskEntity task, Integer version) {
        if (!Objects.equals(task.getVersion(), version)) throw new ScmBusinessException(VERSION_CONFLICT);
    }

    private void requireReason(String reason) {
        if (trimToNull(reason) == null) throw new ScmBusinessException(VALIDATION_ERROR);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void stamp(SortingRecord row, boolean creating) {
        var now = OffsetDateTime.now();
        var operator = ScmOperator.current();
        row.setUpdatedAt(now);
        row.setUpdatedBy(operator);
        if (creating) {
            row.setCreatedAt(now);
            row.setCreatedBy(operator);
        }
    }

    private void save(SortingTaskEntity task) {
        stamp(task, false);
        if (tasks.updateById(task) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
    }
}
