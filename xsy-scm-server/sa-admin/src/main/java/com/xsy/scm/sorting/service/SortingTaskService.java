package com.xsy.scm.sorting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.inventory.dao.InventoryOutboundItemDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.sorting.dao.SortingPartRecordDao;
import com.xsy.scm.sorting.dao.SortingQueryDao;
import com.xsy.scm.sorting.dao.SortingTaskDao;
import com.xsy.scm.sorting.dao.SortingTaskItemDao;
import com.xsy.scm.sorting.domain.dto.SortingOrderLineSnapshot;
import com.xsy.scm.sorting.domain.entity.SortingRecord;
import com.xsy.scm.sorting.domain.entity.SortingTaskEntity;
import com.xsy.scm.sorting.domain.entity.SortingTaskItemEntity;
import com.xsy.scm.sorting.domain.form.SortingActionForm;
import com.xsy.scm.sorting.domain.form.SortingAssignForm;
import com.xsy.scm.sorting.domain.form.SortingEntryForm;
import com.xsy.scm.sorting.domain.form.SortingPartForm;
import com.xsy.scm.sorting.domain.form.SortingTaskCreateForm;
import com.xsy.scm.sorting.domain.vo.SortingPartRecordVO;
import com.xsy.scm.sorting.domain.vo.SortingPrintResultVO;
import com.xsy.scm.sorting.domain.vo.SortingTaskDetailVO;
import com.xsy.scm.sorting.constant.ScmSortingOccupationStatusEnum;
import com.xsy.scm.sorting.constant.ScmSortingResultEnum;
import com.xsy.scm.sorting.constant.ScmSortingTaskStatusEnum;
import com.xsy.scm.sorting.support.SortingAccess;
import com.xsy.scm.supplier.dao.SupplierDao;
import com.xsy.scm.warehouse.dao.WarehouseDao;
import net.lab1024.sa.admin.module.system.employee.dao.EmployeeDao;
import net.lab1024.sa.admin.module.system.employee.domain.entity.EmployeeEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.sorting.constant.SortingConstant.PRINTABLE;
import static com.xsy.scm.sorting.constant.SortingConstant.TASK_NO_PREFIX;
import static com.xsy.scm.sorting.constant.SortingConstant.WORKING;
import static com.xsy.scm.sorting.constant.SortingErrorCode.ASSIGNEE_INVALID;
import static com.xsy.scm.sorting.constant.SortingErrorCode.ITEM_NOT_IN_TASK;
import static com.xsy.scm.sorting.constant.SortingErrorCode.ORDER_LINE_TAKEN;
import static com.xsy.scm.sorting.constant.SortingErrorCode.ORDER_NOT_SORTABLE;
import static com.xsy.scm.sorting.constant.SortingErrorCode.OUTBOUND_EXISTS;
import static com.xsy.scm.sorting.constant.SortingErrorCode.RESULT_INCOMPLETE;
import static com.xsy.scm.sorting.constant.SortingErrorCode.STATE_INVALID;
import static com.xsy.scm.sorting.constant.SortingErrorCode.SUPPLIER_INVALID;
import static com.xsy.scm.sorting.constant.SortingErrorCode.TASK_NOT_FOUND;
import static com.xsy.scm.sorting.constant.SortingErrorCode.WAREHOUSE_INVALID;

/**
 * 分拣只记录实际数量，不直接修改销售订单或库存；完成后需要重开才能修改。
 */
@Service
@RequiredArgsConstructor
public class SortingTaskService {

    /** 一次建单并入的订单行上限，避免生成上千行的巨型任务。 */
    private static final int MAX_LINES_PER_TASK = 500;

    private final InventoryOutboundItemDao inventoryOutboundItemDao;
    /** 只为重开前锁订单行而注入：发车与重开必须在同一批订单行上互相排队。 */
    private final SalesOrderDao salesOrderDao;
    private final SortingTaskDao sortingTaskDao;
    private final SortingTaskItemDao sortingTaskItemDao;
    private final SortingPartRecordDao sortingPartRecordDao;
    private final SortingQueryDao sortingQueryDao;
    private final SortingAccess access;
    private final WarehouseDao warehouseDao;
    private final EmployeeDao employeeDao;
    /** 供应商只读校验（建单时保存来源）：排序域不写供应商表，只按 id 读一行。 */
    private final SupplierDao supplierDao;
    private final ScmIdempotencyService idempotencyService;
    private final SortingQueryService sortingQueryService;
    /** 改实拣量前的包装码绑定校验：跨域只读，排序域不写追溯表。 */
    private final com.xsy.scm.trace.service.TraceUnitService traceUnitService;

    /**
     * 建单并指派。幂等键挡住「同一请求重发生成第二套数据」；不同请求抢同一订单行由部分唯一索引兜底，报可读的占用冲突。
     */
    @Transactional(rollbackFor = Exception.class)
    public SortingTaskDetailVO create(SortingTaskCreateForm form, String key) {
        var claim = idempotencyService.claim("SORTING_TASK_CREATE", key, form);
        if (claim.replay())
            return idempotencyService.replay(claim, SortingTaskDetailVO.class);
        access.requireWarehouse(form.getWarehouseId());
        var warehouse = warehouseDao.selectById(form.getWarehouseId());
        if (warehouse == null || !ScmEnableStatusEnum.ENABLED.name().equals(warehouse.getStatus()))
            throw new ScmBusinessException(WAREHOUSE_INVALID);
        requireUsableAssignee(form.getAssigneeEmployeeId());
        var lines = sortableLines(form.getSalesOrderItemIds());

        var task = new SortingTaskEntity();
        task.setTaskNo(ScmDocumentNumbers.format(TASK_NO_PREFIX, sortingQueryDao.nextNumber()));
        task.setWarehouseId(warehouse.getId());
        task.setWarehouseNameSnapshot(warehouse.getName());
        task.setAssigneeEmployeeId(form.getAssigneeEmployeeId());
        task.setStatus(ScmSortingTaskStatusEnum.PENDING.name());
        String remark = form.getRemark();
        task.setRemark(StringUtils.isBlank(remark) ? null : StringUtils.trim(remark));
        // 三个筛选维度在建单时保存：任务建出来后就不该随主档或配送线路变化。
        // 供应商必须显式给出并校验启用状态；从 SKU 与供应商的关系反推会得到一个
        // 不确定的来源，而分拣台上正是按它找货。
        task.setDeliveryTimeSnapshot(form.getDeliveryTime());
        task.setDeliveryWave(
            StringUtils.isBlank(form.getDeliveryWave()) ? null : StringUtils.trim(form.getDeliveryWave()));
        if (form.getSupplierId() != null) {
            var supplier = supplierDao.selectById(form.getSupplierId());
            if (supplier == null || Boolean.TRUE.equals(supplier.getDeleted())
                || !ScmEnableStatusEnum.ENABLED.name().equals(supplier.getStatus()))
                throw new ScmBusinessException(SUPPLIER_INVALID);
            task.setSupplierId(supplier.getId());
            task.setSupplierNameSnapshot(supplier.getName());
        }
        task.setPrintCount(0);
        stamp(task, true);
        sortingTaskDao.insert(task);

        try {
            for (var line : lines)
                sortingTaskItemDao.insert(newItem(task.getId(), line));
        } catch (DuplicateKeyException taken) {
            throw new ScmBusinessException(ORDER_LINE_TAKEN);
        }
        var result = sortingQueryService.detail(task.getId());
        idempotencyService.complete(claim, "SORTING_TASK", task.getId(), result);
        return result;
    }

    /** 指派 / 改派：换人不换已录入内容；已完成与已取消的任务不再改派。 */
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long id, SortingAssignForm form) {
        ScmDataScopeContext scope = access.scope();
        var task = lock(id);
        access.requireQueueManager(scope, task);
        requireVersion(task, form.getVersion());
        if (!WORKING.contains(task.getStatus()))
            throw new ScmBusinessException(STATE_INVALID);
        requireUsableAssignee(form.getAssigneeEmployeeId());
        task.setAssigneeEmployeeId(form.getAssigneeEmployeeId());
        save(task);
    }

    /**
     * 追加一笔分次分拣记录。只保存分次累计，正式分拣量和结果必须经过 enter 确认。 任务锁让同一任务的追加、编辑、取消、完成串行；重放同样复核当前仓库和受指派人。
     */
    @Transactional(rollbackFor = Exception.class)
    public SortingPartRecordVO appendPart(Long id, SortingPartForm form, String key) {
        ScmDataScopeContext scope = access.scope();
        var task = lock(id);
        access.requireAssignee(scope, task);
        var claim = idempotencyService.claim("SORTING_PART:" + id, key, form);
        if (claim.replay())
            return idempotencyService.replay(claim, SortingPartRecordVO.class);
        if (!WORKING.contains(task.getStatus()))
            throw new ScmBusinessException(STATE_INVALID);
        var row = activeItems(id).get(form.getTaskItemId());
        if (row == null)
            throw new ScmBusinessException(ITEM_NOT_IN_TASK);
        if (!Objects.equals(row.getVersion(), form.getVersion()))
            throw new ScmBusinessException(VERSION_CONFLICT);
        if (row.getResult() != null)
            throw new ScmBusinessException(STATE_INVALID);
        BigDecimal quantity = form.getQuantity();
        if (quantity == null || quantity.signum() <= 0 || quantity.scale() > 4)
            throw new ScmBusinessException(VALIDATION_ERROR);
        var previous = sortingPartRecordDao.latest(row.getId());
        BigDecimal before = previous == null ? BigDecimal.ZERO : previous.getTotalQuantity();
        BigDecimal total = before.add(quantity);
        if (total.compareTo(new BigDecimal("99999999999999.9999")) > 0)
            throw new ScmBusinessException(VALIDATION_ERROR);
        var event = partEvent(id, row.getId(), "APPEND", quantity, total, form.getNote());
        if (sortingPartRecordDao.insert(event) != 1)
            throw new IllegalStateException("分次分拣事件写入失败");
        // 版本递增防止浏览器用分次追加前的草稿直接提交最终结果。
        stamp(row, false);
        if (sortingTaskItemDao.updateById(row) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
        if (ScmSortingTaskStatusEnum.PENDING.name().equals(task.getStatus())) {
            task.setStatus(ScmSortingTaskStatusEnum.SORTING.name());
            if (task.getStartedAt() == null)
                task.setStartedAt(OffsetDateTime.now());
            save(task);
        }
        idempotencyService.complete(claim, "SORTING_PART", event.getId(), event);
        return event;
    }

    private static SortingPartRecordVO partEvent(Long taskId, Long itemId, String kind, BigDecimal delta,
        BigDecimal total, String note) {
        var event = new SortingPartRecordVO();
        event.setTaskId(taskId);
        event.setTaskItemId(itemId);
        event.setEventType(kind);
        event.setQuantityDelta(delta);
        event.setTotalQuantity(total);
        event.setNote(StringUtils.isBlank(note) ? null : StringUtils.trim(note));
        event.setCreatedBy(ScmOperator.current());
        event.setCreatedAt(OffsetDateTime.now());
        return event;
    }

    /**
     * 批量录入分拣结果。允许一次只处理任务里的部分行（边称边录是常态），但每条提交行都必须属于本任务且带它自己读到的版本； 首次录入把任务从 {@code PENDING} 推到 {@code SORTING}。
     */
    @Transactional(rollbackFor = Exception.class)
    public void enter(Long id, SortingEntryForm form) {
        ScmDataScopeContext scope = access.scope();
        var task = lock(id);
        access.requireAssignee(scope, task);
        if (!WORKING.contains(task.getStatus()))
            throw new ScmBusinessException(STATE_INVALID);
        var rows = activeItems(id);
        for (var entry : form.getItems()) {
            var row = rows.get(entry.getId());
            String reason = entry.getReason();
            if (row == null)
                throw new ScmBusinessException(ITEM_NOT_IN_TASK);
            if (!Objects.equals(row.getVersion(), entry.getVersion()))
                throw new ScmBusinessException(VERSION_CONFLICT);
            if (!ScmSortingResultEnum.NORMAL.name().equals(entry.getResult()) && StringUtils.isBlank(reason))
                throw new ScmBusinessException(VALIDATION_ERROR);
            // 历次分量只保存在追加事件表，不能把它当作已确认分拣量。
            // 若最终量偏离已累计记录，必须显式写原因和差额事件，禁止静默覆盖。
            var previous = sortingPartRecordDao.latest(row.getId());
            BigDecimal before = previous != null
                ? previous.getTotalQuantity()
                : row.getSortedQuantity() != null ? row.getSortedQuantity() : BigDecimal.ZERO;
            BigDecimal difference = entry.getSortedQuantity().subtract(before);
            if (previous != null && difference.signum() != 0 && StringUtils.isBlank(reason))
                throw new ScmBusinessException(VALIDATION_ERROR);
            // 已绑定包装码的行不能把实拣量改到有效绑定总量以下；先走解绑更正再改量
            traceUnitService.requireBindingsFitSortedQuantity(row.getId(), entry.getSortedQuantity());
            row.setSortedQuantity(entry.getSortedQuantity());
            row.setResult(entry.getResult());
            row.setReason(StringUtils.isBlank(reason) ? null : StringUtils.trim(reason));
            row.setSortedBy(ScmOperator.current());
            row.setSortedAt(OffsetDateTime.now());
            stamp(row, false);
            // 同一批里重复提交同一行时，第二次带的是已被自己改掉的旧版本，在这里就断掉。
            if (sortingTaskItemDao.updateById(row) != 1)
                throw new ScmBusinessException(VERSION_CONFLICT);
            var correction = partEvent(id, row.getId(), "SET_TOTAL", difference, entry.getSortedQuantity(), reason);
            if (sortingPartRecordDao.insert(correction) != 1)
                throw new IllegalStateException("分拣数量审计写入失败");
        }
        if (ScmSortingTaskStatusEnum.PENDING.name().equals(task.getStatus())) {
            task.setStatus(ScmSortingTaskStatusEnum.SORTING.name());
            if (task.getStartedAt() == null)
                task.setStartedAt(OffsetDateTime.now());
            save(task);
        }
    }

    /** 完成：任务内每条活动明细都必须已有结果。一张订单要分多次完成，就为剩余行另建任务。 */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, SortingActionForm form) {
        ScmDataScopeContext scope = access.scope();
        var task = lock(id);
        access.requireAssignee(scope, task);
        requireVersion(task, form.getVersion());
        if (!WORKING.contains(task.getStatus()))
            throw new ScmBusinessException(STATE_INVALID);
        var rows = activeItems(id).values();
        if (rows.isEmpty() || rows.stream().anyMatch(sortingTaskItem -> sortingTaskItem.getResult() == null))
            throw new ScmBusinessException(RESULT_INCOMPLETE);
        task.setStatus(ScmSortingTaskStatusEnum.COMPLETED.name());
        task.setCompletedAt(OffsetDateTime.now());
        save(task);
    }

    /**
     * 取消：同一事务里释放全部活动明细的占用位，释放后订单行才能进新任务。 已完成的任务要先重开才能取消，否则「完成」会被悄悄抹掉。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, SortingActionForm form) {
        ScmDataScopeContext scope = access.scope();
        var task = lock(id);
        access.requireQueueManager(scope, task);
        requireVersion(task, form.getVersion());
        if (!WORKING.contains(task.getStatus()))
            throw new ScmBusinessException(STATE_INVALID);
        requireReason(form.getReason());
        sortingQueryDao.releaseItems(id, ScmOperator.current());
        task.setStatus(ScmSortingTaskStatusEnum.CANCELLED.name());
        task.setCancelledAt(OffsetDateTime.now());
        save(task);
    }

    /** 重开已完成分拣任务时，保留原分拣数量及原因，供继续修改。已确认销售出库的任务不能重开；发车前会再次检查任务状态。 */
    @Transactional(rollbackFor = Exception.class)
    public void reopen(Long id, SortingActionForm form) {
        ScmDataScopeContext scope = access.scope();
        var task = lock(id);
        access.requireQueueManager(scope, task);
        requireVersion(task, form.getVersion());
        if (!ScmSortingTaskStatusEnum.COMPLETED.name().equals(task.getStatus())) {
            throw new ScmBusinessException(STATE_INVALID);
        }
        requireReason(form.getReason());
        var items = activeItems(id).values();
        // 先锁订单行再查出库，否则「发车提交」与「重开提交」可以交错到两边都成功：
        // 结果是一张已真实出库的订单行还能继续改分拣量，而这正是本条校验要拦的事。
        // 配送侧同样按订单 id 升序加锁（DeliveryRouteService#dispatch），因此这里不会构成反向锁序。
        items.stream().map(SortingTaskItemEntity::getSalesOrderId).filter(Objects::nonNull).distinct().sorted()
            .forEach(salesOrderDao::lock);
        var orderLineIds = items.stream().map(SortingTaskItemEntity::getSalesOrderItemId).filter(Objects::nonNull)
            .toList();
        if (!orderLineIds.isEmpty()
            && !inventoryOutboundItemDao.listOrderLinesWithConfirmedOutbound(orderLineIds).isEmpty()) {
            throw new ScmBusinessException(OUTBOUND_EXISTS);
        }
        task.setStatus(ScmSortingTaskStatusEnum.SORTING.name());
        save(task);
    }

    /**
     * 正式生成打印：只登记计次、时间与操作人。打印不改状态、不写库存，也不递增版本—— 内容没变，让计次把别人的编辑顶成版本冲突是假冲突；计次累加由本方法持有的任务锁串行化。
     */
    @Transactional(rollbackFor = Exception.class)
    public SortingPrintResultVO print(Long id, SortingActionForm form, String key) {
        var claim = idempotencyService.claim("SORTING_PRINT:" + id, key, form);
        if (claim.replay())
            return idempotencyService.replay(claim, SortingPrintResultVO.class);
        ScmDataScopeContext scope = access.scope();
        var task = lock(id);
        access.requireVisible(scope, task);
        requireVersion(task, form.getVersion());
        if (!PRINTABLE.contains(task.getStatus()))
            throw new ScmBusinessException(STATE_INVALID);
        if (sortingQueryDao.markPrinted(id, ScmOperator.current()) != 1)
            throw new ScmBusinessException(STATE_INVALID);
        var result = new SortingPrintResultVO();
        result.setTaskId(task.getId());
        result.setTaskNo(task.getTaskNo());
        result.setItemCount(activeItems(id).size());
        result.setPrintCount(task.getPrintCount() + 1);
        result.setGeneratedAt(OffsetDateTime.now());
        idempotencyService.complete(claim, "SORTING_TASK", id, result);
        return result;
    }

    /** 校验并保存候选订单行：一次读回全部请求行，行数不等即说明有 id 根本不存在。 */
    private List<SortingOrderLineSnapshot> sortableLines(List<Long> requested) {
        var ids = requested.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty() || ids.size() > MAX_LINES_PER_TASK)
            throw new ScmBusinessException(VALIDATION_ERROR);
        var found = sortingQueryDao.orderLines(ids);
        if (found.size() != ids.size())
            throw new ScmBusinessException(ORDER_NOT_SORTABLE);
        for (var line : found) {
            if (!ScmOrderStatusEnum.CONFIRMED.name().equals(line.getOrderStatus())
                || Boolean.TRUE.equals(line.getOrderDeleted()) || Boolean.TRUE.equals(line.getItemDeleted())
                || line.getActualQuantity() == null)
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
        // 计划量 = 建单时保存的订单行实发量；订单侧的值之后怎么变都不影响已生成的任务。
        item.setPlannedQuantitySnapshot(line.getActualQuantity());
        item.setOccupationStatus(ScmSortingOccupationStatusEnum.ACTIVE.name());
        item.setVersion(0);
        stamp(item, true);
        return item;
    }

    /** 任务当前的活动明细，按明细 id 索引。已随取消释放的行不在其中：它们不再代表待办量。 */
    private Map<Long, SortingTaskItemEntity> activeItems(Long taskId) {
        var map = new LinkedHashMap<Long, SortingTaskItemEntity>();
        sortingTaskItemDao
            .selectList(new LambdaQueryWrapper<SortingTaskItemEntity>().eq(SortingTaskItemEntity::getTaskId, taskId)
                .eq(SortingTaskItemEntity::getOccupationStatus, ScmSortingOccupationStatusEnum.ACTIVE.name())
                .orderByAsc(SortingTaskItemEntity::getId))
            .forEach(sortingTaskItem -> map.put(sortingTaskItem.getId(), sortingTaskItem));
        return map;
    }

    private SortingTaskEntity lock(Long id) {
        var task = sortingQueryDao.lockTask(id);
        if (task == null)
            throw new ScmBusinessException(TASK_NOT_FOUND);
        return task;
    }

    /** 未指派是合法状态（建单时可以后派），因此只在显式给了人时校验身份。 */
    private void requireUsableAssignee(Long employeeId) {
        if (employeeId == null)
            return;
        EmployeeEntity employee = employeeDao.selectById(employeeId);
        if (employee == null || Boolean.TRUE.equals(employee.getDeletedFlag())
            || Boolean.TRUE.equals(employee.getDisabledFlag()))
            throw new ScmBusinessException(ASSIGNEE_INVALID);
    }

    private void requireVersion(SortingTaskEntity task, Integer version) {
        if (!Objects.equals(task.getVersion(), version))
            throw new ScmBusinessException(VERSION_CONFLICT);
    }

    private void requireReason(String reason) {
        if (StringUtils.isBlank(reason))
            throw new ScmBusinessException(VALIDATION_ERROR);
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
        if (sortingTaskDao.updateById(task) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
    }
}
