package net.lab1024.sa.admin.module.scm.order.service;

import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import net.lab1024.sa.admin.module.scm.order.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.order.domain.vo.*;
import net.lab1024.sa.admin.module.scm.order.dao.*;
import net.lab1024.sa.admin.module.scm.order.manager.*;
import net.lab1024.sa.admin.module.scm.order.constant.ScmOrderOperationTypeEnum;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryReservationService;

import static net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode.*;
import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Function;

import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
import net.lab1024.sa.admin.module.scm.pricing.service.PriceResolver;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.ResolvedPriceVO;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSkuOptionDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSpuDao;

/**
 * Aggregate root: all item mutations occur inside an order transaction.
 */
@Service
@RequiredArgsConstructor
public class SalesOrderService {
    private final SalesOrderDao orders;
    private final SalesOrderItemDao items;
    private final OrderAddressSnapshotDao addresses;
    private final OrderOperationLogRecorder orderLogs;
    private final CustomerService customers;
    private final PriceResolver prices;
    private final ProductSkuOptionDao skus;
    private final ProductSpuDao spus;
    private final OrderNumberGenerator numbers;
    private final OrderIdempotencyService idempotency;
    /**
     * 确认订单时预留库存、取消时释放（出库波次新增的跨域依赖：order → inventory）。
     */
    private final InventoryReservationService reservations;
    private final SalesOrderQueryService query;
    /** SCM 数据范围的唯一解析入口；只在「能否对这户客户开单」这类归属判定上用。 */
    private final ScmDataScopeService scopeService;

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO create(SalesOrderAddForm f, String key) {
        var claim = idempotency.claim("ORDER_CREATE", key, f);
        if (claim.replay()) return idempotency.replay(claim, SalesOrderDetailVO.class);
        var result = createDraft(f);
        idempotency.complete(claim, "SALES_ORDER", result.getOrderId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO createAndProgress(SalesOrderAddForm f, String key) {
        var claim = idempotency.claim("ORDER_CREATE_AND_PROGRESS", key, f);
        if (claim.replay()) return idempotency.replay(claim, SalesOrderDetailVO.class);
        var result = createDraft(f);
        result = submitOrder(result.getOrderId(), result.getVersion());
        if (result.getItems().stream().allMatch(x -> "STANDARD".equals(x.getProductTypeSnapshot()))) {
            result = confirmOrder(result.getOrderId(), result.getVersion());
        }
        idempotency.complete(claim, "SALES_ORDER", result.getOrderId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderImportResultVO importOrders(List<SalesOrderAddForm> forms, String fileHash, String key, int totalRows) {
        var request = Map.of("fileHash", fileHash, "forms", forms);
        var claim = idempotency.claim("ORDER_IMPORT", key, request);
        if (claim.replay()) return idempotency.replay(claim, SalesOrderImportResultVO.class);
        var result = new SalesOrderImportResultVO();
        result.setTotalRows(totalRows);
        result.setTotalOrders(forms.size());
        var imported = new ArrayList<SalesOrderDetailVO>();
        for (int index = 0; index < forms.size(); index++) {
            try {
                var order = createDraft(forms.get(index));
                order = submitOrder(order.getOrderId(), order.getVersion());
                if (order.getItems().stream().allMatch(x -> "STANDARD".equals(x.getProductTypeSnapshot())))
                    order = confirmOrder(order.getOrderId(), order.getVersion());
                imported.add(order);
            } catch (ScmBusinessException | org.springframework.dao.DataIntegrityViolationException exception) {
                // Let the exception cross the transaction proxy before the import service formats errors.
                throw new ImportOrderException(index, exception);
            }
        }
        result.setOrders(imported);
        result.setConfirmedOrders((int) imported.stream().filter(x -> "CONFIRMED".equals(x.getStatus())).count());
        result.setPendingOrders((int) imported.stream().filter(x -> "PENDING".equals(x.getStatus())).count());
        idempotency.complete(claim, "SALES_ORDER_IMPORT", null, result);
        return result;
    }

    public static final class ImportOrderException extends RuntimeException {
        private final int orderIndex;

        public ImportOrderException(int orderIndex, RuntimeException cause) {
            super(cause);
            this.orderIndex = orderIndex;
        }

        public int getOrderIndex() {
            return orderIndex;
        }
    }

    private SalesOrderDetailVO createDraft(SalesOrderAddForm f) {
        OrderValidator.draft(f);
        var customer = customers.requireTradable(f.getCustomerId());
        // 裁决「P0 基线收口裁决」第 6 条：不能对自己读不到的客户开单。订单负责人取自客户快照，
        // 只收窄列表等于「看不见但仍然能往别人名下塞单」，行级范围就不成立；因此新建入口按同一范围判定。
        // 分配权（scm:customer:assign）与全量订单范围同等放行：主管刚把客户指定给某人，
        // 就该能替他录单，否则「主管建客户 + 指定负责人」这条路会把主管自己挡在门外。
        if (!scopeService.resolve().getOrderSellerScope().allows(customer.getSellerId())
                && !ScmDataScopeService.hasPermission(ScmDataScopeService.CUSTOMER_ASSIGN_PERM))
            throw new ScmDataScopeException();
        validateOriginal(f);
        if (f.getItems().stream().anyMatch(x -> x.getItemId() != null))
            throw new ScmBusinessException(ORDER_ITEM_NOT_OWNED);
        var rows = materialize(f);
        var o = new SalesOrderEntity();
        OrderSnapshotFactory.customer(o, customer);
        o.setOrderNo(numbers.order());
        o.setStatus("DRAFT");
        header(o, f);
        o.setOrderedTotalAmount(total(rows));
        stamp(o, true);
        orders.insert(o);
        for (var row : rows) insert(o.getId(), row);
        var a = new OrderAddressSnapshotEntity();
        BeanUtils.copyProperties(f.getAddress(), a);
        a.setOrderId(o.getId());
        a.setCustomerId(o.getCustomerId());
        a.setCreatedAt(OffsetDateTime.now());
        a.setCreatedBy(ScmOperator.current());
        if (a.getAddress() != null && !a.getAddress().isBlank() && Objects.equals(a.getAddress(), customer.getAddress())) {
            a.setProvinceCode(customer.getProvinceCode());
            a.setProvinceName(customer.getProvinceName());
            a.setCityCode(customer.getCityCode());
            a.setCityName(customer.getCityName());
            a.setDistrictCode(customer.getDistrictCode());
            a.setDistrictName(customer.getDistrictName());
            if (customer.getGeomCrs() != null) {
                a.setLongitude(customer.getLongitude());
                a.setLatitude(customer.getLatitude());
                a.setGeomCrs(customer.getGeomCrs());
            }
        }
        addresses.insert(a);
        var result = query.detailSnapshot(o.getId());
        log(o.getId(), ScmOrderOperationTypeEnum.CREATE, null, null, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO update(SalesOrderUpdateForm f) {
        OrderValidator.draft(f);
        var o = lock(f.getOrderId());
        version(o.getVersion(), f.getVersion());
        OrderStateMachine.editable(o.getStatus());
        // Customer and address identity belong to the creation snapshot; editing cannot replace it.
        if (!Objects.equals(o.getCustomerId(), f.getCustomerId()))
            throw new ScmBusinessException(ORDER_ORIGINAL_INVALID);
        customers.requireTradable(o.getCustomerId());
        validateOriginal(f);
        var oldAddress = addresses.list(o.getId()).getFirst();
        if (!Objects.equals(oldAddress.getReceiverName(), f.getAddress().getReceiverName()) || !Objects.equals(oldAddress.getReceiverPhone(), f.getAddress().getReceiverPhone()) || !Objects.equals(oldAddress.getAddress(), f.getAddress().getAddress()))
            throw new ScmBusinessException(ORDER_STATE_INVALID);
        var before = query.detailSnapshot(o.getId());
        var existing = items.list(o.getId());
        var requested = materialize(f);
        var changes = SalesOrderItemChangeSet.between(existing, requested);
        // Remove before insert so a removed SKU may be added again without violating the active unique index.
        for (var row : changes.removed()) removeItem(row);
        for (var row : changes.updated()) {
            row.setOrderId(o.getId());
            saveItem(row);
        }
        for (var row : changes.inserted()) insert(o.getId(), row);
        header(o, f);
        o.setOrderedTotalAmount(total(requested));
        save(o);
        var result = query.detailSnapshot(o.getId());
        log(o.getId(), ScmOrderOperationTypeEnum.UPDATE, null, before, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO submit(OrderVersionForm f, String key) {
        var claim = idempotency.claim("ORDER_SUBMIT:" + f.getOrderId(), key, f);
        if (claim.replay()) return idempotency.replay(claim, SalesOrderDetailVO.class);
        var result = submitOrder(f.getOrderId(), f.getVersion());
        idempotency.complete(claim, "SALES_ORDER", result.getOrderId(), result);
        return result;
    }

    private SalesOrderDetailVO submitOrder(Long orderId, Integer expectedVersion) {
        var o = lock(orderId);
        version(o.getVersion(), expectedVersion);
        OrderStateMachine.transition(o.getStatus(), "PENDING");
        var before = query.detailSnapshot(o.getId());
        var rows = items.list(o.getId());
        var automatic = rows.stream().filter(x -> !x.getManualPriceOverride()).map(SalesOrderItemEntity::getSkuId).toList();
        customers.requireTradable(o.getCustomerId());
        var resolved = prices.requireResolvable(o.getCustomerId(), automatic, OffsetDateTime.now()).stream().collect(Collectors.toMap(ResolvedPriceVO::getSkuId, Function.identity()));
        var manual = rows.stream().filter(SalesOrderItemEntity::getManualPriceOverride).map(SalesOrderItemEntity::getSkuId).toList();
        var manualResolved = prices.resolve(o.getCustomerId(), manual, OffsetDateTime.now());
        if (manualResolved.stream().anyMatch(x -> !x.isSellable()))
            throw new ScmBusinessException(net.lab1024.sa.admin.module.scm.pricing.constant.PricingErrorCode.SKU_NOT_SELLABLE);
        for (var row : rows) {
            if (!row.getManualPriceOverride()) OrderSnapshotFactory.applyPrice(row, resolved.get(row.getSkuId()));
            else {
                OrderValidator.reason(row.getManualPriceReason(), ORDER_PRICE_OVERRIDE_REASON_REQUIRED);
                if (row.getDraftUnitPrice() == null) throw new ScmBusinessException(ORDER_PRICE_INVALID);
            }
            row.setLockedUnitPrice(row.getDraftUnitPrice());
            row.setLockedPriceSource(row.getDraftPriceSource());
            row.setLockedPriceSourceId(row.getDraftPriceSourceId());
            row.setOrderedLineAmount(OrderAmountCalculator.lineAmount(row.getOrderedQuantity(), row.getLockedUnitPrice()));
            if ("STANDARD".equals(row.getProductTypeSnapshot())) {
                row.setActualQuantity(row.getOrderedQuantity());
                row.setActualQuantitySource("SYSTEM");
            }
            saveItem(row);
        }
        o.setOrderedTotalAmount(total(rows));
        o.setStatus("PENDING");
        o.setSubmittedAt(OffsetDateTime.now());
        save(o);
        var result = query.detailSnapshot(o.getId());
        log(o.getId(), ScmOrderOperationTypeEnum.SUBMIT, null, before, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO actualQuantity(OrderActualQuantityForm f, String key) {
        var claim = idempotency.claim("ORDER_ACTUAL:" + f.getOrderId() + ":" + f.getItemId(), key, f);
        if (claim.replay()) return idempotency.replay(claim, SalesOrderDetailVO.class);
        var o = lock(f.getOrderId());
        OrderStateMachine.actualQuantity(o.getStatus());
        var row = items.list(o.getId()).stream().filter(x -> Objects.equals(x.getId(), f.getItemId())).findFirst().orElseThrow(() -> new ScmBusinessException(ORDER_ITEM_NOT_OWNED));
        if (!"NON_STANDARD".equals(row.getProductTypeSnapshot()))
            throw new ScmBusinessException(ORDER_ACTUAL_NOT_ALLOWED);
        if (!Objects.equals(row.getVersion(), f.getVersion()))
            throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);
        OrderValidator.reason(f.getReason(), ORDER_ACTUAL_REASON_REQUIRED);
        var before = query.detailSnapshot(o.getId());
        row.setActualQuantity(OrderValidator.decimal(f.getActualQuantity(), true));
        row.setActualQuantitySource("MANUAL");
        row.setActualQuantityReason(f.getReason().trim());
        saveItem(row);
        // Advance the aggregate version too: stale confirm forms must refresh after any item change.
        save(o);
        var result = query.detailSnapshot(o.getId());
        log(o.getId(), ScmOrderOperationTypeEnum.ACTUAL_QUANTITY, f.getReason(), before, result);
        idempotency.complete(claim, "SALES_ORDER", o.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO confirm(OrderVersionForm f, String key) {
        var claim = idempotency.claim("ORDER_CONFIRM:" + f.getOrderId(), key, f);
        if (claim.replay()) return idempotency.replay(claim, SalesOrderDetailVO.class);
        var result = confirmOrder(f.getOrderId(), f.getVersion());
        idempotency.complete(claim, "SALES_ORDER", result.getOrderId(), result);
        return result;
    }

    private SalesOrderDetailVO confirmOrder(Long orderId, Integer expectedVersion) {
        var o = lock(orderId);
        version(o.getVersion(), expectedVersion);
        OrderStateMachine.transition(o.getStatus(), "CONFIRMED");
        var before = query.detailSnapshot(o.getId());
        var rows = items.list(o.getId());
        for (var row : rows) {
            if (row.getActualQuantity() == null || row.getActualQuantity().signum() <= 0)
                throw new ScmBusinessException(ORDER_ACTUAL_QUANTITY_REQUIRED);
            row.setSettlementLineAmount(OrderAmountCalculator.lineAmount(row.getActualQuantity(), row.getLockedUnitPrice()));
            saveItem(row);
        }
        o.setSettlementTotalAmount(OrderAmountCalculator.orderAmount(rows.stream().map(SalesOrderItemEntity::getSettlementLineAmount).toList()));
        o.setStatus("CONFIRMED");
        o.setConfirmedAt(OffsetDateTime.now());
        save(o);
        // 订单确认不自动预留库存；当前主链是先接单、再采购和收货，预留由后续显式动作完成。
        var result = query.detailSnapshot(o.getId());
        log(o.getId(), ScmOrderOperationTypeEnum.CONFIRM, null, before, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO cancel(OrderCancelForm f, String key) {
        var claim = idempotency.claim("ORDER_CANCEL:" + f.getOrderId(), key, f);
        if (claim.replay()) return idempotency.replay(claim, SalesOrderDetailVO.class);
        var o = lock(f.getOrderId());
        version(o.getVersion(), f.getVersion());
        OrderStateMachine.transition(o.getStatus(), "CANCELLED");
        OrderValidator.reason(f.getReason(), ORDER_CANCEL_REASON_REQUIRED);
        var before = query.detailSnapshot(o.getId());
        o.setStatus("CANCELLED");
        o.setCancelReason(f.getReason().trim());
        o.setCancelledAt(OffsetDateTime.now());
        save(o);
        // 取消时释放该订单的预留（若存在）。当前没有触发点会创建预留，因此通常是空操作；
        // 保留这行是为了让「预留一旦启用」时取消路径自动正确，不需要再改这里。
        reservations.releaseBySalesOrder(o.getId());
        var result = query.detailSnapshot(o.getId());
        log(o.getId(), ScmOrderOperationTypeEnum.CANCEL, f.getReason(), before, result);
        idempotency.complete(claim, "SALES_ORDER", o.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(OrderVersionForm f) {
        var o = orders.lock(f.getOrderId());
        if (o == null) return;
        version(o.getVersion(), f.getVersion());
        if (!"DRAFT".equals(o.getStatus())) throw new ScmBusinessException(ORDER_DELETE_STATE_INVALID);
        var before = query.detailSnapshot(o.getId());
        for (var row : items.list(o.getId())) removeItem(row);
        if (orders.softDelete(o.getId(), o.getVersion(), ScmOperator.current()) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
        log(o.getId(), ScmOrderOperationTypeEnum.UPDATE, "删除草稿", before, Map.of("deleted", true, "version", o.getVersion() + 1));
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(OrderBatchDeleteForm f) {
        for (var row : f.getOrders().stream().sorted(Comparator.comparing(OrderVersionForm::getOrderId)).toList())
            delete(row);
    }

    /**
     * 为已确认的订单**显式预留库存**（出库波次）。
     *
     * <p><b>为什么是显式操作而不是确认时自动预留</b>：本业务的链路是
     * 「客户下单 → 订单 → 确认 → 聚合 → 采购需求 → 采购单 → 收货 → 库存」，
     * **库存在订单确认之后才产生**。把预留挂在确认上等于要求「货先到才能接单」，
     * 与「先接单、再采购」的设计前提冲突（实测会让 82 个集成用例报 41011）。
     * 因此把「什么时候占货」交给业务判断：货到之后，由业务人员对本单执行预留。
     *
     * <p>业务依据仍是销售订单 —— 预留的来源单据就是订单行，不存在「凭空占货」。
     *
     * <p>严格语义：任一行可用量不足就抛 41011，整体回滚（不会只占一半）。
     * 仓库由 {@code defaultEnabledWarehouse} 解析（订单无仓库字段，G-03 单仓库）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reserveStock(Long orderId) {
        var o = lock(orderId);
        if (!"CONFIRMED".equals(o.getStatus())) throw new ScmBusinessException(ORDER_RESERVE_STATE_INVALID);
        var rows = items.list(o.getId());
        reservations.reserveForSalesOrder(o.getId(),
                rows.stream()
                        .map(r -> new InventoryReservationService.OrderReserveLine(r.getId(), r.getSkuId(), r.getActualQuantity()))
                        .toList(),
                OffsetDateTime.now());
        log(o.getId(), ScmOrderOperationTypeEnum.RESERVE_STOCK, null, null, Map.of("reservedLines", rows.size()));
    }

    public SalesOrderEntity lock(Long id) {
        var o = orders.lock(id);
        if (o == null) throw new ScmBusinessException(ORDER_NOT_FOUND);
        return o;
    }

    public static void version(Integer actual, Integer expected) {
        if (!Objects.equals(actual, expected)) throw new ScmBusinessException(VERSION_CONFLICT);
    }

    private void validateOriginal(SalesOrderAddForm f) {
        if (f.getOriginalOrderId() == null) return;
        var original = orders.selectById(f.getOriginalOrderId());
        if (original == null || !"CONFIRMED".equals(original.getStatus()) || !Objects.equals(original.getCustomerId(), f.getCustomerId()))
            throw new ScmBusinessException(ORDER_ORIGINAL_INVALID);
    }

    private List<SalesOrderItemEntity> materialize(SalesOrderAddForm f) {
        var ids = f.getItems().stream().map(SalesOrderItemForm::getSkuId).toList();
        var products = skus.selectByIds(ids).stream().collect(Collectors.toMap(x -> x.getSkuId(), Function.identity()));
        var spuIds = products.values().stream().map(x -> x.getSpuId()).distinct().toList();
        var codes = spuIds.isEmpty() ? Map.<Long, String>of() : spus.selectBatchIds(spuIds).stream().collect(Collectors.toMap(x -> x.getId(), x -> x.getSpuCode()));
        var resolved = prices.resolve(f.getCustomerId(), ids, OffsetDateTime.now()).stream().collect(Collectors.toMap(ResolvedPriceVO::getSkuId, Function.identity()));
        return f.getItems().stream().map(x -> {
            var sku = products.get(x.getSkuId());
            return OrderSnapshotFactory.item(x, sku, sku == null ? null : codes.get(sku.getSpuId()), resolved.get(x.getSkuId()));
        }).toList();
    }

    private void header(SalesOrderEntity o, SalesOrderAddForm f) {
        o.setOrderSource(f.getOrderSource());
        o.setOriginalOrderId(f.getOriginalOrderId());
        o.setSupplementReason(OrderValidator.trim(f.getSupplementReason()));
        o.setRemark(OrderValidator.trim(f.getRemark()));
        o.setExpectDeliveryTime(f.getExpectDeliveryTime());
    }

    private BigDecimal total(List<SalesOrderItemEntity> rows) {
        return OrderAmountCalculator.orderAmount(rows.stream().map(SalesOrderItemEntity::getOrderedLineAmount).toList());
    }

    private void insert(Long id, SalesOrderItemEntity row) {
        row.setOrderId(id);
        row.setVersion(0);
        row.setCreatedAt(OffsetDateTime.now());
        row.setCreatedBy(ScmOperator.current());
        row.setUpdatedAt(row.getCreatedAt());
        row.setUpdatedBy(row.getCreatedBy());
        items.insert(row);
    }

    private void stamp(SalesOrderEntity o, boolean creating) {
        o.setUpdatedAt(OffsetDateTime.now());
        o.setUpdatedBy(ScmOperator.current());
        if (creating) {
            o.setCreatedAt(o.getUpdatedAt());
            o.setCreatedBy(o.getUpdatedBy());
        }
    }

    private void save(SalesOrderEntity o) {
        stamp(o, false);
        if (orders.updateById(o) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
    }

    private void saveItem(SalesOrderItemEntity row) {
        row.setUpdatedAt(OffsetDateTime.now());
        row.setUpdatedBy(ScmOperator.current());
        if (items.updateById(row) != 1) throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);
    }

    private void removeItem(SalesOrderItemEntity row) {
        if (items.softDelete(row.getId(), row.getVersion(), ScmOperator.current()) != 1)
            throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);
    }

    private void log(Long id, ScmOrderOperationTypeEnum operation, String reason, Object before, Object after) {
        orderLogs.record(id, operation, reason, before, after);
    }
}
