package net.lab1024.sa.admin.module.scm.purchase.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSkuOptionDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSpuDao;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductSpuEntity;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSkuOptionVO;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmPurchaseOperationTypeEnum;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandAllocationDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOperationLogDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderItemDao;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderBatchDeleteForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderCancelForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderDeleteForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderShortCloseForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderUpdateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderVersionForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderAllocationVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseAmountCalculator;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseDemandAllocator;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderAllocationChangeSet;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderItemChangeSet;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderStateMachine;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderValidator;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseSnapshotFactory;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierEntity;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierSkuEntity;
import net.lab1024.sa.admin.module.scm.warehouse.domain.entity.WarehouseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_CANCEL_REASON_REQUIRED;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_DELETE_STATE_INVALID;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_EMPTY;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_NOT_OWNED;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_ITEM_VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_STATE_INVALID;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_SHORT_CLOSE_REASON_REQUIRED;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_SUPPLIER_SKU_DISABLED;

/**
 * 采购单命令服务（W5 Target Design §4.2 T1–T6 / §7.8 / §7.9）。
 *
 * <p><b>Q13 是本类存在的核心理由</b>：`update` 必须做**两层**差量对账 ——
 * <ol>
 *   <li><b>行级</b>（{@link PurchaseOrderItemChangeSet}）：身份 = {@code (purchase_order_id, sku_id)}；</li>
 *   <li><b>分配级</b>（{@link PurchaseOrderAllocationChangeSet}）：身份 =
 *       {@code (purchase_order_item_id, purchase_demand_id)}。</li>
 * </ol>
 * 两层身份必须分开：把「一行一个 demandId」的写法搬进来（A 源的 A-D23）会让
 * 「一行多需求」在编辑时只剩最后一条。因此本类**从不**按「item → 单个 allocation」取数，
 * 只按**集合**做差量。
 *
 * <p><b>锁序（P12）</b>：
 * <ul>
 *   <li>{@code create}：需求（按 id 升序）→ 采购单 → 采购行（§7.9）；</li>
 *   <li>{@code update}：采购单 → 采购行 → 现有分配 → 需求（旧 ∪ 新，按 id 升序）（§7.9 明列此序）；</li>
 *   <li>{@code cancel} / {@code delete}：采购单 → 采购行 → 分配 → 需求（释放用）。</li>
 * </ul>
 *
 * <p><b>需求侧回落（§7.8 C 段的必然后果）</b>：`cancel` 与 `delete` 会把本单的分配全部软删，
 * 并**按旧 ∪ 新并集重算** `purchase_demand.allocated_quantity` / `status`。
 * 若不释放，需求会永久卡在 {@code ALLOCATED}、后续再也分不出去 —— 这与 §4.4
 * 「三态可回落」直接矛盾。`shortClose` **不释放**：已有实收，冲销口径是 R12/G1，不在 W5。
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private static final String SCOPE_CREATE = "PURCHASE_ORDER_CREATE";

    private final PurchaseOrderDao purchaseOrderDao;

    private final PurchaseOrderItemDao purchaseOrderItemDao;

    private final PurchaseDemandDao purchaseDemandDao;

    private final PurchaseDemandAllocationDao purchaseDemandAllocationDao;

    private final PurchaseOperationLogDao purchaseOperationLogDao;

    private final ProductSkuOptionDao skus;

    private final ProductSpuDao spus;

    private final PurchaseNumberGenerator numberGenerator;

    private final PurchaseIdempotencyService idempotencyService;

    private final PurchaseQueryService queryService;

    private final PurchaseOrderValidator purchaseOrderValidator;

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

        List<RequestedRow> rows = materialize(form);

        // P12 锁序第 1 层：purchase_demand（按 id 升序），必须早于采购单写入
        Map<Long, PurchaseDemandEntity> demands = lockDemands(requestedDemandIds(rows));
        // 新建：本单此前不存在任何分配 → 旧合计为空
        Map<Long, BigDecimal> newTotals =
                validateAllocations(rows, demands, form.getSupplierId(), form.getWarehouseId(), Map.of());

        PurchaseOrderEntity order = PurchaseSnapshotFactory.order(
                supplier.getSupplierCode(), supplier.getName(),
                warehouse.getWarehouseCode(), warehouse.getName(), form);
        order.setOrderNo(numberGenerator.order());
        order.setTotalAmount(totalAmount(rows));
        stamp(order, true);
        purchaseOrderDao.insert(order);

        for (int index = 0; index < rows.size(); index++) {
            RequestedRow row = rows.get(index);
            row.item.setPurchaseOrderId(order.getId());
            row.item.setSortOrder(index);
            stamp(row.item, true);
            purchaseOrderItemDao.insert(row.item);
            insertAllocations(row);
        }

        // 本单此前不存在任何分配 → oldTotals 为空
        recomputeDemands(demands, Map.of(), newTotals, form.getSupplierId());

        PurchaseOrderVO result = queryService.orderDetail(order.getId());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.CREATE, order.getId(), null, null, null,
                orderSnapshot(result)));
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
                loadAllocations(existing);

        List<RequestedRow> rows = materialize(form);
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
        Collection<Long> involved = new LinkedHashSet<>(requestedDemandIds(rows));
        existingAllocations.values().forEach(list ->
                list.forEach(allocation -> involved.add(allocation.getPurchaseDemandId())));
        Map<Long, PurchaseDemandEntity> demands = lockDemands(involved);

        // 本单**已有**的分配合计（按 demandId）。校验新请求时必须先把它减掉 ——
        // 库里的 `demand.allocated_quantity` 已经包含了本单的旧分配，直接相加会把自己数两遍，
        // 于是「数量没变的一次编辑」也会撞 40082。
        Map<Long, BigDecimal> oldTotals = totals(existingAllocations.values());
        Map<Long, BigDecimal> newTotals =
                validateAllocations(rows, demands, form.getSupplierId(), form.getWarehouseId(), oldTotals);

        PurchaseOrderItemChangeSet itemChanges = PurchaseOrderItemChangeSet.between(
                existing, rows.stream().map(row -> row.item).toList());

        PurchaseOrderVO before = queryService.orderDetail(order.getId());

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
                stamp(row.item, true);
                purchaseOrderItemDao.insert(row.item);
            } else {
                stamp(row.item, false);
                if (purchaseOrderItemDao.updateById(row.item) != 1) {
                    throw new ScmBusinessException(PURCHASE_ORDER_ITEM_VERSION_CONFLICT);
                }
            }
            applyAllocationChanges(row, existingAllocations.getOrDefault(row.item.getId(), List.of()));
        }

        recomputeDemands(demands, oldTotals, newTotals, form.getSupplierId());

        order.setSupplierId(form.getSupplierId());
        order.setSupplierCodeSnapshot(supplier.getSupplierCode());
        order.setSupplierNameSnapshot(supplier.getName());
        order.setPurchaserId(form.getPurchaserId());
        order.setWarehouseId(form.getWarehouseId());
        order.setWarehouseCodeSnapshot(warehouse.getWarehouseCode());
        order.setWarehouseNameSnapshot(warehouse.getName());
        order.setPlannedArrivalDate(form.getPlannedArrivalDate());
        order.setRemark(PurchaseOrderValidator.trim(form.getRemark()));
        order.setTotalAmount(totalAmount(rows));
        save(order);

        PurchaseOrderVO result = queryService.orderDetail(order.getId());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.UPDATE, order.getId(), null, null,
                orderSnapshot(before), orderSnapshot(result)));
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

        Map<String, Object> before = stateSnapshot(order);
        order.setStatus("SUBMITTED");
        order.setSubmittedAt(OffsetDateTime.now());
        save(order);

        PurchaseOrderVO result = queryService.orderDetail(order.getId());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.SUBMIT, order.getId(), null, null,
                before, stateSnapshot(order)));
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
        releaseAllocations(order);

        Map<String, Object> before = stateSnapshot(order);
        order.setStatus("CANCELLED");
        order.setCancelReason(PurchaseOrderValidator.trim(form.getCancelReason()));
        order.setCancelledAt(OffsetDateTime.now());
        save(order);

        Map<String, Object> after = stateSnapshot(order);
        after.put("cancelReason", order.getCancelReason());
        PurchaseOrderVO result = queryService.orderDetail(order.getId());
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

        Map<String, Object> before = stateSnapshot(order);
        order.setStatus("SHORT_CLOSED");
        order.setShortCloseReason(PurchaseOrderValidator.trim(form.getShortCloseReason()));
        order.setShortClosedAt(OffsetDateTime.now());
        save(order);

        Map<String, Object> after = stateSnapshot(order);
        after.put("shortCloseReason", order.getShortCloseReason());
        PurchaseOrderVO result = queryService.orderDetail(order.getId());
        purchaseOperationLogDao.append(PurchaseSnapshotFactory.operationLog(
                ScmPurchaseOperationTypeEnum.SHORT_CLOSE, order.getId(), null,
                order.getShortCloseReason(), before, after));
        idempotencyService.complete(claim, "PURCHASE_ORDER", order.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(PurchaseOrderDeleteForm form) {
        PurchaseOrderEntity order = purchaseOrderDao.lock(form.getId());
        if (order == null) {
            // 幂等：已删除视为成功（同 W4 的 delete 语义）
            return;
        }
        if (!PurchaseOrderStateMachine.editable(order.getStatus())) {
            throw new ScmBusinessException(PURCHASE_ORDER_DELETE_STATE_INVALID);
        }

        PurchaseOrderVO before = queryService.orderDetail(order.getId());
        releaseAllocations(order);

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
                orderSnapshot(before), after));
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(PurchaseOrderBatchDeleteForm form) {
        // 按 id 升序：批量删除也必须遵守确定性锁序（P12）
        form.getOrders().stream()
                .sorted(Comparator.comparing(PurchaseOrderVersionForm::getId))
                .forEach(row -> delete(deleteForm(row)));
    }

    // ------------------------------------------------------------------
    // 装配与校验
    // ------------------------------------------------------------------

    /**
     * 请求行 + 请求分配（Q13：一行 N 条分配）。
     *
     * <p>`allocations` 在校验阶段被填充为真正的 {@link PurchaseDemandAllocationEntity}
     * —— 因为快照字段（`salesOrderId` / `salesOrderItemId` / `skuId` / `demandSnapshot`）
     * 只能从**已锁定的需求行**取，不能在装配商品时凭空造。
     */
    private static final class RequestedRow {

        private final PurchaseOrderItemEntity item;

        private final List<PurchaseOrderAddForm.Allocation> forms;

        private final List<PurchaseDemandAllocationEntity> allocations = new ArrayList<>();

        private RequestedRow(PurchaseOrderItemEntity item, List<PurchaseOrderAddForm.Allocation> forms) {
            this.item = item;
            this.forms = forms == null ? List.of() : forms;
        }
    }

    private List<RequestedRow> materialize(PurchaseOrderAddForm form) {
        List<Long> skuIds = form.getItems().stream()
                .map(PurchaseOrderAddForm.Item::getSkuId)
                .distinct()
                .toList();
        Map<Long, ProductSkuOptionVO> products = skus.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(ProductSkuOptionVO::getSkuId, Function.identity(), (a, b) -> a));
        List<Long> spuIds = products.values().stream()
                .map(ProductSkuOptionVO::getSpuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> spuCodes = spuIds.isEmpty()
                ? Map.of()
                : spus.selectBatchIds(spuIds).stream().collect(Collectors.toMap(
                        ProductSpuEntity::getId, ProductSpuEntity::getSpuCode, (a, b) -> a));

        List<RequestedRow> rows = new ArrayList<>(form.getItems().size());
        for (PurchaseOrderAddForm.Item itemForm : form.getItems()) {
            ProductSkuOptionVO sku = products.get(itemForm.getSkuId());
            if (sku == null) {
                // 商品不存在 / 不可售：与 W2 的采购判定入口共用同一个对外码（40992），
                // 不让 W1 的内部码从采购 API 泄漏出去
                throw new ScmBusinessException(PURCHASE_SUPPLIER_SKU_DISABLED);
            }
            SupplierSkuEntity supplierSku =
                    purchaseOrderValidator.requirePurchasableSku(form.getSupplierId(), itemForm.getSkuId());
            PurchaseOrderItemEntity item = PurchaseSnapshotFactory.item(
                    itemForm, sku, spuCodes.get(sku.getSpuId()), supplierSku);
            rows.add(new RequestedRow(item, itemForm.getAllocations()));
        }
        return rows;
    }

    /**
     * 校验分配集合并补全快照字段；返回「本次请求按 demandId 的合计」。
     *
     * <p>逐条对应 §7.8 B 段第 7 步与 §7.4 的分配校验表。
     *
     * <p><b>累计上限必须扣除本单旧值</b>：`demand.allocated_quantity` 是**全库**已分配合计，
     * 其中已含本单上一次提交的量。`create` 时本单尚无分配（传空 Map），`update` 时必须传入
     * 旧合计，否则「原样保存」都会因为 `旧 + 新 > required` 而误报 40082。
     * 这与 {@code recomputeDemands} 里 `otherAllocated = allocated − oldTotals[demand]` 是同一个口径。
     */
    private Map<Long, BigDecimal> validateAllocations(List<RequestedRow> rows,
                                                      Map<Long, PurchaseDemandEntity> demands,
                                                      Long supplierId,
                                                      Long warehouseId,
                                                      Map<Long, BigDecimal> oldTotals) {
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        for (RequestedRow row : rows) {
            for (PurchaseOrderAddForm.Allocation allocationForm : row.forms) {
                PurchaseDemandEntity demand = demands.get(allocationForm.getDemandId());
                if (demand == null) {
                    throw new ScmBusinessException(PURCHASE_DEMAND_NOT_FOUND);
                }
                // 需求版本必填（40091）+ 必须等于当前版本（40972）
                PurchaseDemandAllocator.demandVersion(allocationForm.getDemandVersion(), demand.getVersion());
                // 同一 SKU 才能挂（40995）
                PurchaseDemandAllocator.itemMatchesDemand(row.item.getSkuId(), demand.getSkuId());
                // Q17：需求单位必须等于采购单位，W5 不换算（40971）
                PurchaseDemandAllocator.unitCompatible(
                        demand.getDemandUnitSnapshot(), row.item.getPurchaseUnitSnapshot());
                // (supplier, warehouse) 一致性；首次分配时由调用方固定 supplier（40981）
                PurchaseDemandAllocator.assignmentCompatible(supplierId, warehouseId, demand);

                BigDecimal quantity = PurchaseOrderValidator.decimal(allocationForm.getQuantity(), true);
                totals.merge(demand.getId(), quantity, BigDecimal::add);

                PurchaseDemandAllocationEntity allocation = new PurchaseDemandAllocationEntity();
                // 保留行此刻已有真实 itemId（身份的一半）；新增行落库后再回填
                allocation.setPurchaseOrderItemId(row.item.getId());
                allocation.setPurchaseDemandId(demand.getId());
                allocation.setSalesOrderId(demand.getSalesOrderId());
                allocation.setSalesOrderItemId(demand.getSalesOrderItemId());
                allocation.setSkuId(demand.getSkuId());
                allocation.setAllocatedQuantity(quantity);
                allocation.setDemandSnapshot(PurchaseSnapshotFactory.allocationDemandSnapshot(demand));
                row.allocations.add(allocation);
            }
        }

        for (Map.Entry<Long, BigDecimal> entry : totals.entrySet()) {
            PurchaseDemandEntity demand = demands.get(entry.getKey());
            // 扣除本单旧分配后再比 required（见方法注释）。create 时 oldTotals 为空 → 退化为原口径。
            BigDecimal otherAllocated = demand.getAllocatedQuantity()
                    .subtract(oldTotals.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            PurchaseDemandAllocator.withinRequired(demand.getRequiredQuantity(),
                    otherAllocated.add(entry.getValue()));
        }
        return totals;
    }

    /**
     * §7.8 C 段：按 `旧 ∪ 新` 的 demandId 升序逐个重算需求侧。
     *
     * <p>**必须遍历并集**：只在旧集合出现的 demand（被删空 / 整单取消）也要重算，
     * 否则 `allocated_quantity` 不会回落、`status` 也不会从 `ALLOCATED` 退回 `PENDING`。
     */
    private void recomputeDemands(Map<Long, PurchaseDemandEntity> locked,
                                  Map<Long, BigDecimal> oldTotals,
                                  Map<Long, BigDecimal> newTotals,
                                  Long orderSupplierId) {
        Collection<Long> involved = new LinkedHashSet<>(oldTotals.keySet());
        involved.addAll(newTotals.keySet());
        for (Long demandId : PurchaseDemandAllocator.ascendingDemandIds(involved)) {
            PurchaseDemandEntity demand = locked.get(demandId);
            if (demand == null) {
                throw new ScmBusinessException(PURCHASE_DEMAND_NOT_FOUND);
            }
            BigDecimal otherAllocated = demand.getAllocatedQuantity()
                    .subtract(oldTotals.getOrDefault(demandId, BigDecimal.ZERO));
            BigDecimal finalAllocated = otherAllocated
                    .add(newTotals.getOrDefault(demandId, BigDecimal.ZERO));
            PurchaseDemandAllocator.withinRequired(demand.getRequiredQuantity(), finalAllocated);

            Long supplierId = demand.getSupplierId();
            if (PurchaseDemandAllocator.shouldFixSupplier(demand) && finalAllocated.signum() > 0) {
                // supplier 由「第一次分配」固定，之后不得改变（§7.4）
                supplierId = orderSupplierId;
            }
            String status = PurchaseDemandAllocator.statusFor(
                    demand.getRequiredQuantity(), finalAllocated);
            if (purchaseDemandDao.updateAllocation(demand.getId(), demand.getVersion(),
                    finalAllocated, status, supplierId, ScmOperator.current()) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        }
    }

    /** 单行内的分配集合差量（Q13：**禁止**「一个 item 对一个 allocation」的算法）。 */
    private void applyAllocationChanges(RequestedRow row,
                                        List<PurchaseDemandAllocationEntity> existing) {
        PurchaseOrderAllocationChangeSet changes =
                PurchaseOrderAllocationChangeSet.between(existing, row.allocations);
        for (PurchaseDemandAllocationEntity removed : changes.removed()) {
            // 只删这一条：同一行的其它 allocation 必须原样保留
            if (purchaseDemandAllocationDao.softDelete(
                    removed.getId(), removed.getVersion(), ScmOperator.current()) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        }
        for (PurchaseDemandAllocationEntity updated : changes.updated()) {
            // 不重建行：同 (item, demand) 的其它分配不受影响
            if (purchaseDemandAllocationDao.updateQuantity(updated.getId(), updated.getVersion(),
                    updated.getAllocatedQuantity(), ScmOperator.current()) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        }
        for (PurchaseDemandAllocationEntity inserted : changes.inserted()) {
            stamp(inserted, true);
            purchaseDemandAllocationDao.insert(inserted);
        }
    }

    /** 新建行的全部分配（没有旧集合可对账）。 */
    private void insertAllocations(RequestedRow row) {
        for (PurchaseDemandAllocationEntity allocation : row.allocations) {
            allocation.setPurchaseOrderItemId(row.item.getId());
            stamp(allocation, true);
            purchaseDemandAllocationDao.insert(allocation);
        }
    }

    /**
     * 释放本单的全部分配并重算需求（`cancel` / `delete`）。
     *
     * <p>见类注释：不释放会让需求永久卡在 {@code ALLOCATED}。**不删任何行、不删任何单据**
     * —— 只把分配软删、把需求的 `allocated_quantity` 减回去。
     */
    private void releaseAllocations(PurchaseOrderEntity order) {
        List<PurchaseOrderItemEntity> items = purchaseOrderItemDao.listByOrderId(order.getId());
        if (items.isEmpty()) {
            return;
        }
        Map<Long, List<PurchaseDemandAllocationEntity>> byItem = loadAllocations(items);
        List<PurchaseDemandAllocationEntity> allocations = byItem.values().stream()
                .flatMap(List::stream)
                .toList();
        if (allocations.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> oldTotals = totals(byItem.values());
        Map<Long, PurchaseDemandEntity> demands = lockDemands(oldTotals.keySet());
        for (PurchaseOrderItemEntity item : items) {
            purchaseDemandAllocationDao.softDeleteByOrderItemId(item.getId(), ScmOperator.current());
        }
        recomputeDemands(demands, oldTotals, Map.of(), order.getSupplierId());
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private Map<Long, List<PurchaseDemandAllocationEntity>> loadAllocations(
            List<PurchaseOrderItemEntity> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<Long> itemIds = items.stream().map(PurchaseOrderItemEntity::getId).toList();
        return purchaseDemandAllocationDao.listActiveByOrderItemIds(itemIds).stream()
                .collect(Collectors.groupingBy(PurchaseDemandAllocationEntity::getPurchaseOrderItemId));
    }

    private static List<Long> requestedDemandIds(List<RequestedRow> rows) {
        return rows.stream()
                .flatMap(row -> row.forms.stream())
                .map(PurchaseOrderAddForm.Allocation::getDemandId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /** P12 锁序第 1 层：需求必须**按 id 升序**一次性锁完（{@code ORDER BY id ASC FOR UPDATE}）。 */
    private Map<Long, PurchaseDemandEntity> lockDemands(Collection<Long> demandIds) {
        List<Long> ascending = PurchaseDemandAllocator.ascendingDemandIds(demandIds);
        if (ascending.isEmpty()) {
            return Map.of();
        }
        List<PurchaseDemandEntity> rows = purchaseDemandDao.lockByIds(ascending);
        if (rows.size() != ascending.size()) {
            throw new ScmBusinessException(PURCHASE_DEMAND_NOT_FOUND);
        }
        Map<Long, PurchaseDemandEntity> byId = new LinkedHashMap<>();
        rows.forEach(row -> byId.put(row.getId(), row));
        return byId;
    }

    private static Map<Long, BigDecimal> totals(Collection<List<PurchaseDemandAllocationEntity>> grouped) {
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        grouped.stream().flatMap(List::stream).forEach(allocation ->
                totals.merge(allocation.getPurchaseDemandId(),
                        allocation.getAllocatedQuantity(), BigDecimal::add));
        return totals;
    }

    private static BigDecimal totalAmount(List<RequestedRow> rows) {
        return PurchaseAmountCalculator.totalAmount(
                rows.stream().map(row -> row.item.getLineAmount()).toList());
    }

    private PurchaseOrderEntity lockOrder(Long id) {
        PurchaseOrderEntity order = purchaseOrderDao.lock(id);
        if (order == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
        }
        return order;
    }

    private static void version(Integer actual, Integer expected) {
        if (!Objects.equals(actual, expected)) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    private void save(PurchaseOrderEntity order) {
        stamp(order, false);
        if (purchaseOrderDao.updateById(order) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    private static void stamp(PurchaseOrderEntity row, boolean creating) {
        OffsetDateTime now = OffsetDateTime.now();
        row.setUpdatedAt(now);
        row.setUpdatedBy(ScmOperator.current());
        if (creating) {
            row.setCreatedAt(now);
            row.setCreatedBy(row.getUpdatedBy());
        }
    }

    private static void stamp(PurchaseOrderItemEntity row, boolean creating) {
        OffsetDateTime now = OffsetDateTime.now();
        row.setUpdatedAt(now);
        row.setUpdatedBy(ScmOperator.current());
        if (creating) {
            row.setVersion(0);
            row.setDeleted(false);
            row.setCreatedAt(now);
            row.setCreatedBy(row.getUpdatedBy());
        }
    }

    private static void stamp(PurchaseDemandAllocationEntity row, boolean creating) {
        OffsetDateTime now = OffsetDateTime.now();
        row.setUpdatedAt(now);
        row.setUpdatedBy(ScmOperator.current());
        if (creating) {
            row.setVersion(0);
            row.setDeleted(false);
            row.setCreatedAt(now);
            row.setCreatedBy(row.getUpdatedBy());
        }
    }

    private static PurchaseOrderDeleteForm deleteForm(PurchaseOrderVersionForm row) {
        PurchaseOrderDeleteForm form = new PurchaseOrderDeleteForm();
        form.setId(row.getId());
        return form;
    }

    /** `SUBMIT` / `CANCEL` / `SHORT_CLOSE` 的轻量状态快照（§7.12）。 */
    private static Map<String, Object> stateSnapshot(PurchaseOrderEntity order) {
        Map<String, Object> snapshot = PurchaseSnapshotFactory.snapshot();
        snapshot.put("status", order.getStatus());
        snapshot.put("version", order.getVersion());
        return snapshot;
    }

    /** `CREATE` / `UPDATE` / `DELETE` 的「全量 header + items」快照（§7.12）。 */
    private static Map<String, Object> orderSnapshot(PurchaseOrderVO vo) {
        Map<String, Object> snapshot = PurchaseSnapshotFactory.snapshot();
        snapshot.put("id", vo.getId());
        snapshot.put("orderNo", vo.getOrderNo());
        snapshot.put("supplierId", vo.getSupplierId());
        snapshot.put("warehouseId", vo.getWarehouseId());
        snapshot.put("purchaserId", vo.getPurchaserId());
        snapshot.put("status", vo.getStatus());
        snapshot.put("plannedArrivalDate",
                vo.getPlannedArrivalDate() == null ? null : vo.getPlannedArrivalDate().toString());
        snapshot.put("totalAmount", PurchaseSnapshotFactory.fixed(vo.getTotalAmount()));
        snapshot.put("version", vo.getVersion());
        snapshot.put("items", itemSnapshots(vo.getItems()));
        return snapshot;
    }

    private static List<Map<String, Object>> itemSnapshots(List<PurchaseOrderItemVO> items) {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        if (items == null) {
            return snapshots;
        }
        for (PurchaseOrderItemVO item : items) {
            Map<String, Object> row = PurchaseSnapshotFactory.snapshot();
            row.put("id", item.getId());
            row.put("skuId", item.getSkuId());
            row.put("plannedQuantity", PurchaseSnapshotFactory.fixed(item.getPlannedQuantity()));
            row.put("receivedQuantity", PurchaseSnapshotFactory.fixed(item.getReceivedQuantity()));
            row.put("purchasePrice", PurchaseSnapshotFactory.fixed(item.getPurchasePrice()));
            row.put("lineAmount", PurchaseSnapshotFactory.fixed(item.getLineAmount()));
            row.put("sortOrder", item.getSortOrder());
            row.put("version", item.getVersion());
            List<Map<String, Object>> allocations = new ArrayList<>();
            if (item.getAllocations() != null) {
                for (PurchaseOrderAllocationVO allocation : item.getAllocations()) {
                    Map<String, Object> one = PurchaseSnapshotFactory.snapshot();
                    one.put("allocationId", allocation.getAllocationId());
                    one.put("demandId", allocation.getDemandId());
                    one.put("quantity", PurchaseSnapshotFactory.fixed(allocation.getQuantity()));
                    allocations.add(one);
                }
            }
            row.put("allocations", allocations);
            snapshots.add(row);
        }
        return snapshots;
    }
}
