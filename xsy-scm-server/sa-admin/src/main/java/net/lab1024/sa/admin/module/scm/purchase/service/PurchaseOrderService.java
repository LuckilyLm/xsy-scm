package net.lab1024.sa.admin.module.scm.purchase.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmPurchaseOperationTypeEnum;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandAllocationDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOperationLogDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderItemDao;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderBatchDeleteForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderBatchShortCloseForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderCancelForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderDeleteForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderReassignForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderShortCloseForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderUpdateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderVersionForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseAmountCalculator;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseEntityStamper;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderAuditSnapshotFactory;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderItemChangeSet;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderStateMachine;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderValidator;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseSnapshotFactory;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseOrderAllocationService.RequestedRow;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseOwnerResolver;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierEntity;
import net.lab1024.sa.admin.module.scm.warehouse.domain.entity.WarehouseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_CANCEL_REASON_REQUIRED;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_DELETE_STATE_INVALID;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_EMPTY;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_NOT_OWNED;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_STATE_INVALID;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_SHORT_CLOSE_REASON_REQUIRED;

/**
 * 采购单命令编排：幂等、状态转换、写入顺序、操作日志与事务边界。
 * 行级身份为 (order, sku)，分配级身份为 (item, demand)，两层独立对账，保留一行多需求。
 * 新建先锁需求再写单据；编辑先锁单据和采购行，再由分配服务锁旧、新需求并集。
 * 取消和删除持有单据锁后释放分配并重算需求；少收关单保留已有分配。
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private static final String SCOPE_CREATE = "PURCHASE_ORDER_CREATE";

    private final PurchaseOrderDao purchaseOrderDao;

    private final PurchaseOrderItemDao purchaseOrderItemDao;

    private final PurchaseDemandAllocationDao purchaseDemandAllocationDao;

    private final PurchaseOperationLogDao purchaseOperationLogDao;

    private final PurchaseNumberGenerator numberGenerator;

    private final PurchaseIdempotencyService idempotencyService;

    private final PurchaseQueryService queryService;

    private final PurchaseOrderValidator purchaseOrderValidator;

    private final PurchaseOrderAllocationService allocationService;

    private final PurchaseOwnerResolver ownerResolver;

    // ------------------------------------------------------------------
    // T1 create
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO create(PurchaseOrderAddForm form, String idempotencyKey) {
        var claim = idempotencyService.claim(SCOPE_CREATE, idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PurchaseOrderVO.class);
        }

        PurchaseOrderValidator.draft(form);
        if (form.getItems().stream().anyMatch(item -> item.getId() != null)) {
            // 新建不接受任何既有行 id（同 W4 的 ORDER_ITEM_NOT_OWNED）
            throw new ScmBusinessException(PURCHASE_ORDER_ITEM_NOT_OWNED);
        }
        SupplierEntity supplier = purchaseOrderValidator.requireEnabledSupplier(form.getSupplierId());
        WarehouseEntity warehouse = purchaseOrderValidator.requireEnabledWarehouse(form.getWarehouseId());

        List<RequestedRow> rows = allocationService.materialize(form);

        // P12 锁序第 1 层：purchase_demand（按 id 升序），必须早于采购单写入
        Map<Long, PurchaseDemandEntity> demands =
                allocationService.lockDemands(PurchaseOrderAllocationService.requestedDemandIds(rows));
        // 新建：本单此前不存在任何分配 → 旧合计为空
        Map<Long, BigDecimal> newTotals =
                allocationService.validateAllocations(rows, demands, form.getSupplierId(), form.getWarehouseId(), Map.of());

        PurchaseOrderEntity order = PurchaseSnapshotFactory.order(
                supplier.getSupplierCode(), supplier.getName(),
                warehouse.getWarehouseCode(), warehouse.getName(),
                // 归属由服务端裁决：普通新建一律是当前员工，表单里的 purchaser_id 不采信（裁决第 7 条）
                ownerResolver.resolveForCreate(form.getPurchaserId()), form);
        order.setOrderNo(numberGenerator.order());
        order.setTotalAmount(totalAmount(rows));
        PurchaseEntityStamper.stamp(order, true);
        purchaseOrderDao.insert(order);

        for (int index = 0; index < rows.size(); index++) {
            RequestedRow row = rows.get(index);
            row.item.setPurchaseOrderId(order.getId());
            row.item.setSortOrder(index);
            PurchaseEntityStamper.stamp(row.item, true);
            purchaseOrderItemDao.insert(row.item);
            allocationService.insertAllocations(row);
        }

        // 本单此前不存在任何分配 → oldTotals 为空
        allocationService.recomputeDemands(demands, Map.of(), newTotals, form.getSupplierId());

        PurchaseOrderVO result = queryService.orderDetailForCommand(order.getId());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.CREATE, order.getId(), null, null, null,
                PurchaseOrderAuditSnapshotFactory.orderAuditSnapshot(result)));
        idempotencyService.complete(claim, "PURCHASE_ORDER", order.getId(), result);
        return result;
    }

    // ------------------------------------------------------------------
    // T2 update（§7.8 A–D 四段）
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO update(PurchaseOrderUpdateForm form) {
        PurchaseOrderValidator.draft(form);
        PurchaseOrderEntity order = lockOrder(form.getId());
        version(order.getVersion(), form.getVersion());
        // 只有 DRAFT 可编辑行与分配（T2）。注意这里**必须判返回值**：
        // `editable(...)` 是纯布尔判定，写成裸语句会静默放过 SUBMITTED / 终态单，
        // 让后续的分配校验（40082）抢先抛出，把一个状态错误伪装成数量错误。
        if (!PurchaseOrderStateMachine.editable(order.getStatus())) {
            throw new ScmBusinessException(PURCHASE_ORDER_STATE_INVALID);
        }

        SupplierEntity supplier = purchaseOrderValidator.requireEnabledSupplier(form.getSupplierId());
        WarehouseEntity warehouse = purchaseOrderValidator.requireEnabledWarehouse(form.getWarehouseId());

        // 锁序：采购单 → 采购行（按 id 升序）→ 现有分配
        List<PurchaseOrderItemEntity> existing = purchaseOrderItemDao.lockByOrderId(order.getId());
        Map<Long, PurchaseOrderItemEntity> existingById = existing.stream()
                .collect(Collectors.toMap(PurchaseOrderItemEntity::getId, Function.identity(), (a, b) -> a));
        Map<Long, List<PurchaseDemandAllocationEntity>> existingAllocations =
                allocationService.loadAllocations(existing);

        List<RequestedRow> rows = allocationService.materialize(form);
        // 保留行沿用库中的已收数量：请求只表达「计划量」，不表达「已收量」。
        // （DRAFT 单的已收恒为 0，但把不变量写出来比依赖它更安全 —— 否则一个可编辑状态
        //   的松动就会把 received_quantity 静默清 0。）
        for (RequestedRow row : rows) {
            PurchaseOrderItemEntity old = existingById.get(row.item.getId());
            if (old != null) {
                row.item.setReceivedQuantity(old.getReceivedQuantity());
            }
        }

        // 锁序：需求（旧 ∪ 新，按 id 升序）—— 并集是硬要求，只在旧集合出现的 demand
        // 也必须锁，否则删除后 allocated 不会回落（§7.8 C 段）
        Collection<Long> involved = new LinkedHashSet<>(PurchaseOrderAllocationService.requestedDemandIds(rows));
        existingAllocations.values().forEach(list ->
                list.forEach(allocation -> involved.add(allocation.getPurchaseDemandId())));
        Map<Long, PurchaseDemandEntity> demands = allocationService.lockDemands(involved);

        // 本单**已有**的分配合计（按 demandId）。校验新请求时必须先把它减掉 ——
        // 库里的 `demand.allocated_quantity` 已经包含了本单的旧分配，直接相加会把自己数两遍，
        // 于是「数量没变的一次编辑」也会撞 40082。
        Map<Long, BigDecimal> oldTotals = PurchaseOrderAllocationService.totals(existingAllocations.values());
        Map<Long, BigDecimal> newTotals =
                allocationService.validateAllocations(rows, demands, form.getSupplierId(), form.getWarehouseId(), oldTotals);

        PurchaseOrderItemChangeSet itemChanges = PurchaseOrderItemChangeSet.between(
                existing, rows.stream().map(row -> row.item).toList());

        PurchaseOrderVO before = queryService.orderDetailForCommand(order.getId());

        // 先删后插：被删行的 SKU 允许在同一次请求里作为新行重新出现，
        // 否则会撞 uk_purchase_order_item_order_sku_active（同 W4 的处理）
        for (PurchaseOrderItemEntity removed : itemChanges.removed()) {
            purchaseDemandAllocationDao.softDeleteByOrderItemId(removed.getId(), ScmOperator.current());
            if (purchaseOrderItemDao.softDelete(
                    removed.getId(), removed.getVersion(), ScmOperator.current()) != 1) {
                throw new ScmBusinessException(PURCHASE_ORDER_ITEM_VERSION_CONFLICT);
            }
        }

        for (int index = 0; index < rows.size(); index++) {
            RequestedRow row = rows.get(index);
            row.item.setPurchaseOrderId(order.getId());
            row.item.setSortOrder(index);
            if (row.item.getId() == null) {
                PurchaseEntityStamper.stamp(row.item, true);
                purchaseOrderItemDao.insert(row.item);
            } else {
                PurchaseEntityStamper.stamp(row.item, false);
                if (purchaseOrderItemDao.updateById(row.item) != 1) {
                    throw new ScmBusinessException(PURCHASE_ORDER_ITEM_VERSION_CONFLICT);
                }
            }
            allocationService.applyAllocationChanges(row, existingAllocations.getOrDefault(row.item.getId(), List.of()));
        }

        allocationService.recomputeDemands(demands, oldTotals, newTotals, form.getSupplierId());

        order.setSupplierId(form.getSupplierId());
        order.setSupplierCodeSnapshot(supplier.getSupplierCode());
        order.setSupplierNameSnapshot(supplier.getName());
        // 归属不在编辑接口里移动：order 是锁出来的库中行，purchaser_id 原样写回，
        // 表单值对任何角色（含分配权持有者）都不采信 —— 改派只有 /reassign 一条路（裁决第 7 条）。
        order.setWarehouseId(form.getWarehouseId());
        order.setWarehouseCodeSnapshot(warehouse.getWarehouseCode());
        order.setWarehouseNameSnapshot(warehouse.getName());
        order.setPlannedArrivalDate(form.getPlannedArrivalDate());
        order.setRemark(PurchaseOrderValidator.trim(form.getRemark()));
        order.setTotalAmount(totalAmount(rows));
        save(order);

        PurchaseOrderVO result = queryService.orderDetailForCommand(order.getId());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.UPDATE, order.getId(), null, null,
                PurchaseOrderAuditSnapshotFactory.orderAuditSnapshot(before), PurchaseOrderAuditSnapshotFactory.orderAuditSnapshot(result)));
        return result;
    }

    // ------------------------------------------------------------------
    // T2.1 reassign（改派采购归属）
    // ------------------------------------------------------------------

    /**
     * 改派采购归属：只有持 {@code scm:purchase:assign} 的调用方能到达（权限在控制器上）。
     *
     * <p>刻意不按单据状态设限：裁决只要求「有分配权才可指定 / 改派」，
     * 而单据在途时换人（离职、调岗）恰恰是本端点的主要用途。
     *
     * <p>乐观锁沿用本模块既有纪律：先 {@code FOR UPDATE} 锁单，比对 {@code id + version}，
     * 再由 {@code @Version} 的 {@code updateById} 做并发下的第二道防线（0 行 → 40921）。
     * 改派必须留操作日志：归属是数据范围依据，换了谁必须可追溯。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO reassign(PurchaseOrderReassignForm form) {
        PurchaseOrderEntity order = lockOrder(form.getId());
        version(order.getVersion(), form.getVersion());
        if (Objects.equals(order.getPurchaserId(), form.getPurchaserId())) {
            // 同值改派不推进版本，也不留一条 before == after 的噪声日志
            return queryService.orderDetailForCommand(order.getId());
        }

        Map<String, Object> before = PurchaseOrderAuditSnapshotFactory.orderStateSnapshot(order);
        before.put("purchaserId", order.getPurchaserId());
        order.setPurchaserId(form.getPurchaserId());
        save(order);

        Map<String, Object> after = PurchaseOrderAuditSnapshotFactory.orderStateSnapshot(order);
        after.put("purchaserId", order.getPurchaserId());
        PurchaseOrderVO result = queryService.orderDetailForCommand(order.getId());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.UPDATE, order.getId(), null, form.getReason(), before, after));
        return result;
    }

    // ------------------------------------------------------------------
    // T3 submit / T4 cancel / T5 shortClose / T6 delete
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO submit(PurchaseOrderVersionForm form, String idempotencyKey) {
        var claim = idempotencyService.claim(
                "PURCHASE_ORDER_SUBMIT:" + form.getId(), idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PurchaseOrderVO.class);
        }

        PurchaseOrderEntity order = lockOrder(form.getId());
        version(order.getVersion(), form.getVersion());
        PurchaseOrderStateMachine.transition(order.getStatus(), "SUBMITTED");
        if (purchaseOrderItemDao.countActiveByOrderId(order.getId()) == 0) {
            throw new ScmBusinessException(PURCHASE_ORDER_ITEM_EMPTY);
        }

        Map<String, Object> before = PurchaseOrderAuditSnapshotFactory.orderStateSnapshot(order);
        order.setStatus("SUBMITTED");
        order.setSubmittedAt(OffsetDateTime.now());
        save(order);

        PurchaseOrderVO result = queryService.orderDetailForCommand(order.getId());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.SUBMIT, order.getId(), null, null,
                before, PurchaseOrderAuditSnapshotFactory.orderStateSnapshot(order)));
        idempotencyService.complete(claim, "PURCHASE_ORDER", order.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO cancel(PurchaseOrderCancelForm form, String idempotencyKey) {
        var claim = idempotencyService.claim(
                "PURCHASE_ORDER_CANCEL:" + form.getId(), idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PurchaseOrderVO.class);
        }

        PurchaseOrderEntity order = lockOrder(form.getId());
        version(order.getVersion(), form.getVersion());
        PurchaseOrderStateMachine.transition(order.getStatus(), "CANCELLED");
        PurchaseOrderValidator.reason(form.getCancelReason(), PURCHASE_CANCEL_REASON_REQUIRED);

        // 释放本单的全部分配并重算需求（§7.8 C 段不变量：allocated 必须能回落）
        allocationService.releaseAllocations(order);

        Map<String, Object> before = PurchaseOrderAuditSnapshotFactory.orderStateSnapshot(order);
        order.setStatus("CANCELLED");
        order.setCancelReason(PurchaseOrderValidator.trim(form.getCancelReason()));
        order.setCancelledAt(OffsetDateTime.now());
        save(order);

        Map<String, Object> after = PurchaseOrderAuditSnapshotFactory.orderStateSnapshot(order);
        after.put("cancelReason", order.getCancelReason());
        PurchaseOrderVO result = queryService.orderDetailForCommand(order.getId());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.CANCEL, order.getId(), null, order.getCancelReason(),
                before, after));
        idempotencyService.complete(claim, "PURCHASE_ORDER", order.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO shortClose(PurchaseOrderShortCloseForm form, String idempotencyKey) {
        var claim = idempotencyService.claim(
                "PURCHASE_ORDER_SHORT_CLOSE:" + form.getId(), idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, PurchaseOrderVO.class);
        }

        PurchaseOrderVO result = applyShortClose(form);
        idempotencyService.complete(claim, "PURCHASE_ORDER", form.getId(), result);
        return result;
    }

    /**
     * 少收关单的核心转换：锁单 → 版本校验 → 状态机 → 「至少一行已收且一行未收齐」→ 落库 → 操作日志。
     *
     * <p>单单命令与批量命令共用此方法，二者对合法性 / 版本 / 原因的要求完全一致；区别只在批量命令
     * 不走每单幂等（整批在同一事务内要么全成要么全回滚，§6.8）。
     */
    private PurchaseOrderVO applyShortClose(PurchaseOrderShortCloseForm form) {
        PurchaseOrderEntity order = lockOrder(form.getId());
        version(order.getVersion(), form.getVersion());
        PurchaseOrderStateMachine.transition(order.getStatus(), "SHORT_CLOSED");
        PurchaseOrderValidator.reason(form.getShortCloseReason(), PURCHASE_SHORT_CLOSE_REASON_REQUIRED);

        List<PurchaseOrderItemEntity> rows = purchaseOrderItemDao.listByOrderId(order.getId());
        boolean anyReceived = rows.stream().anyMatch(row ->
                row.getReceivedQuantity() != null && row.getReceivedQuantity().signum() > 0);
        boolean anyOutstanding = rows.stream().anyMatch(row ->
                row.getReceivedQuantity() == null
                        || row.getReceivedQuantity().compareTo(row.getPlannedQuantity()) < 0);
        if (!anyReceived || !anyOutstanding) {
            // 「至少一行已收 且 至少一行未收齐」——两个条件缺一，少收关单就失去语义
            throw new ScmBusinessException(PURCHASE_ORDER_STATE_INVALID);
        }

        Map<String, Object> before = PurchaseOrderAuditSnapshotFactory.orderStateSnapshot(order);
        order.setStatus("SHORT_CLOSED");
        order.setShortCloseReason(PurchaseOrderValidator.trim(form.getShortCloseReason()));
        order.setShortClosedAt(OffsetDateTime.now());
        save(order);

        Map<String, Object> after = PurchaseOrderAuditSnapshotFactory.orderStateSnapshot(order);
        after.put("shortCloseReason", order.getShortCloseReason());
        PurchaseOrderVO result = queryService.orderDetailForCommand(order.getId());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.SHORT_CLOSE, order.getId(), null,
                order.getShortCloseReason(), before, after));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(PurchaseOrderDeleteForm form) {
        PurchaseOrderEntity order = purchaseOrderDao.lock(form.getId());
        if (order == null) {
            // 幂等：已删除视为成功（同 W4 的 delete 语义）
            return;
        }
        // 删除没走 lockOrder，归属守卫单独补一次；批量删除逐单委托本方法，因此同样生效
        ownerResolver.requireVisible(order.getPurchaserId());
        if (!PurchaseOrderStateMachine.editable(order.getStatus())) {
            throw new ScmBusinessException(PURCHASE_ORDER_DELETE_STATE_INVALID);
        }

        PurchaseOrderVO before = queryService.orderDetailForCommand(order.getId());
        allocationService.releaseAllocations(order);

        for (PurchaseOrderItemEntity row : purchaseOrderItemDao.listByOrderId(order.getId())) {
            if (purchaseOrderItemDao.softDelete(
                    row.getId(), row.getVersion(), ScmOperator.current()) != 1) {
                throw new ScmBusinessException(PURCHASE_ORDER_ITEM_VERSION_CONFLICT);
            }
        }
        if (purchaseOrderDao.softDelete(
                order.getId(), order.getVersion(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }

        Map<String, Object> after = PurchaseSnapshotFactory.snapshot();
        after.put("deleted", true);
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.DELETE, order.getId(), null, null,
                PurchaseOrderAuditSnapshotFactory.orderAuditSnapshot(before), after));
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(PurchaseOrderBatchDeleteForm form) {
        // 按 id 升序：批量删除也必须遵守确定性锁序（P12）
        form.getOrders().stream()
                .sorted(Comparator.comparing(PurchaseOrderVersionForm::getId))
                .forEach(row -> delete(deleteForm(row)));
    }

    /**
     * 批量少收关单（Wave 2B §6.3）。整批共享原因，逐单套用与单单 {@link #applyShortClose} 完全相同的
     * 合法性 / 版本 / 状态校验；本方法自带 {@code @Transactional}，任一单非法即整批回滚——不存在部分成功。
     * 锁序按 id 升序（P12），与批量删除一致，避免交叉持锁死锁。
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchShortClose(PurchaseOrderBatchShortCloseForm form) {
        form.getOrders().stream()
                .sorted(Comparator.comparing(PurchaseOrderVersionForm::getId))
                .forEach(row -> applyShortClose(shortCloseForm(row, form.getShortCloseReason())));
    }

    private static PurchaseOrderShortCloseForm shortCloseForm(PurchaseOrderVersionForm row, String reason) {
        PurchaseOrderShortCloseForm form = new PurchaseOrderShortCloseForm();
        form.setId(row.getId());
        form.setVersion(row.getVersion());
        form.setShortCloseReason(reason);
        return form;
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private static BigDecimal totalAmount(List<RequestedRow> rows) {
        return PurchaseAmountCalculator.totalAmount(
                rows.stream().map(row -> row.item.getLineAmount()).toList());
    }

    private PurchaseOrderEntity lockOrder(Long id) {
        PurchaseOrderEntity order = purchaseOrderDao.lock(id);
        if (order == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
        }
        // 写侧归属：本方法是五条单据命令（编辑/改派/提交/取消/少收关单）的唯一取单入口，
        // 判在这里等于「看不到就动不了」，新增命令只要沿用 lockOrder 即自动带上这条边界。
        ownerResolver.requireVisible(order.getPurchaserId());
        return order;
    }

    private static void version(Integer actual, Integer expected) {
        if (!Objects.equals(actual, expected)) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    private void save(PurchaseOrderEntity order) {
        PurchaseEntityStamper.stamp(order, false);
        if (purchaseOrderDao.updateById(order) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    private static PurchaseOrderDeleteForm deleteForm(PurchaseOrderVersionForm row) {
        PurchaseOrderDeleteForm form = new PurchaseOrderDeleteForm();
        form.setId(row.getId());
        return form;
    }
}
