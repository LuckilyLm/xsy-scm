package com.xsy.scm.order.service;

import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.order.domain.entity.OrderAddressSnapshotEntity;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;

import com.xsy.scm.order.domain.form.OrderActualQuantityForm;
import com.xsy.scm.order.domain.form.OrderBatchDeleteForm;
import com.xsy.scm.order.domain.form.OrderCancelForm;
import com.xsy.scm.order.domain.form.OrderVersionForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderItemForm;
import com.xsy.scm.order.domain.form.SalesOrderUpdateForm;

import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderImportResultVO;

import com.xsy.scm.order.dao.OrderAddressSnapshotDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;

import com.xsy.scm.order.manager.OrderAmountCalculator;
import com.xsy.scm.order.manager.OrderOperationLogRecorder;
import com.xsy.scm.order.manager.OrderSnapshotFactory;
import com.xsy.scm.order.manager.OrderStateMachine;
import com.xsy.scm.order.manager.OrderValidator;
import com.xsy.scm.order.manager.SalesOrderItemChangeSet;

import com.xsy.scm.order.constant.ScmOrderOperationTypeEnum;
import com.xsy.scm.order.constant.ScmOrderProductTypeEnum;
import com.xsy.scm.order.constant.ScmOrderQuantitySourceEnum;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.finance.constant.ScmFinanceReceivableSourceTypeEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.inventory.service.InventoryReservationService;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ACTUAL_NOT_ALLOWED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ACTUAL_QUANTITY_REQUIRED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ACTUAL_REASON_REQUIRED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_CANCEL_REASON_REQUIRED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_DELETE_STATE_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ITEM_NOT_OWNED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ITEM_VERSION_CONFLICT;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_NOT_FOUND;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_ORIGINAL_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_PRICE_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_PRICE_OVERRIDE_REASON_REQUIRED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RESERVE_STATE_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_STATE_INVALID;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.stream.Collectors;
import java.util.function.Function;

import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.pricing.service.PriceResolver;
import com.xsy.scm.pricing.domain.vo.ResolvedPriceVO;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.dao.ProductSpuDao;

/**
 * Aggregate root: all item mutations occur inside an order transaction.
 */
@Service
@RequiredArgsConstructor
public class SalesOrderService {
    private final SalesOrderDao salesOrderDao;
    private final SalesOrderItemDao salesOrderItemDao;
    private final OrderAddressSnapshotDao orderAddressSnapshotDao;
    private final OrderOperationLogRecorder orderLogs;
    private final CustomerService customerService;
    private final PriceResolver priceResolver;
    private final ProductSkuOptionDao productSkuOptionDao;
    private final ProductSpuDao productSpuDao;
    private final OrderNumberGenerator numbers;
    private final OrderIdempotencyService orderIdempotencyService;
    /**
     * 确认订单时预留库存、取消时释放；库存变更通过库存域服务完成。
     */
    private final InventoryReservationService inventoryReservationService;
    private final SalesOrderQueryService salesOrderQueryService;
    /** SCM 数据范围的唯一解析入口；只在「能否对这户客户开单」这类归属判定上用。 */
    private final ScmDataScopeService dataScopeService;

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO create(SalesOrderAddForm salesOrderAddForm, String key) {
        var claim = orderIdempotencyService.claim("ORDER_CREATE", key, salesOrderAddForm);
        if (claim.replay()) return orderIdempotencyService.replay(claim, SalesOrderDetailVO.class);
        var result = createDraft(salesOrderAddForm);
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name(),
            result.getOrderId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO createAndProgress(SalesOrderAddForm salesOrderAddForm, String key) {
        var claim = orderIdempotencyService.claim("ORDER_CREATE_AND_PROGRESS", key, salesOrderAddForm);
        if (claim.replay()) return orderIdempotencyService.replay(claim, SalesOrderDetailVO.class);
        var result = createDraft(salesOrderAddForm);
        result = submitOrder(result.getOrderId(), result.getVersion());
        if (result.getItems().stream().allMatch(
                orderItem -> ScmOrderProductTypeEnum.STANDARD.name().equals(orderItem.getProductTypeSnapshot()))) {
            result = confirmOrder(result.getOrderId(), result.getVersion());
        }
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name(),
            result.getOrderId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderImportResultVO importOrders(List<SalesOrderAddForm> forms, String fileHash, String key, int totalRows) {
        var request = Map.of("fileHash", fileHash, "forms", forms);
        var claim = orderIdempotencyService.claim("ORDER_IMPORT", key, request);
        if (claim.replay()) return orderIdempotencyService.replay(claim, SalesOrderImportResultVO.class);
        var result = new SalesOrderImportResultVO();
        result.setTotalRows(totalRows);
        result.setTotalOrders(forms.size());
        var imported = new ArrayList<SalesOrderDetailVO>();
        for (int index = 0; index < forms.size(); index++) {
            try {
                var order = createDraft(forms.get(index));
                order = submitOrder(order.getOrderId(), order.getVersion());
                if (order.getItems().stream().allMatch(
                        orderItem -> ScmOrderProductTypeEnum.STANDARD.name().equals(orderItem.getProductTypeSnapshot())))
                    order = confirmOrder(order.getOrderId(), order.getVersion());
                imported.add(order);
            } catch (ScmBusinessException | org.springframework.dao.DataIntegrityViolationException exception) {
                // Let the exception cross the transaction proxy before the import service formats errors.
                throw new ImportOrderException(index, exception);
            }
        }
        result.setOrders(imported);
        result.setConfirmedOrders(
                (int) imported.stream()
                        .filter(importedOrder -> ScmOrderStatusEnum.CONFIRMED.name().equals(importedOrder.getStatus()))
                        .count());
        result.setPendingOrders(
                (int) imported.stream()
                        .filter(importedOrder -> ScmOrderStatusEnum.PENDING.name().equals(importedOrder.getStatus()))
                        .count());
        orderIdempotencyService.complete(claim, "SALES_ORDER_IMPORT", null, result);
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

    private SalesOrderDetailVO createDraft(SalesOrderAddForm salesOrderAddForm) {
        OrderValidator.draft(salesOrderAddForm);
        var customer = customerService.requireTradable(salesOrderAddForm.getCustomerId());
        // 新建订单前必须确认当前调用者能读取该客户；订单负责人取自客户快照，
        // 只收窄列表等于「看不见但仍然能往别人名下塞单」，行级范围就不成立；因此新建入口按同一范围判定。
        // 分配权（scm:customer:assign）与全量订单范围同等放行：主管刚把客户指定给某人，
        // 就该能替他录单，否则「主管建客户 + 指定负责人」这条路会把主管自己挡在门外。
        if (!dataScopeService.resolve().getOrderSellerScope().allows(customer.getSellerId())
                && !ScmDataScopeService.hasPermission(ScmDataScopeService.CUSTOMER_ASSIGN_PERM))
            throw new ScmDataScopeException();
        validateOriginal(salesOrderAddForm);
        if (salesOrderAddForm.getItems().stream().anyMatch(orderItemForm -> orderItemForm.getItemId() != null))
            throw new ScmBusinessException(ORDER_ITEM_NOT_OWNED);
        var rows = materialize(salesOrderAddForm);
        var salesOrder = new SalesOrderEntity();
        OrderSnapshotFactory.customer(salesOrder, customer);
        salesOrder.setOrderNo(numbers.order());
        salesOrder.setStatus(ScmOrderStatusEnum.DRAFT.name());
        header(salesOrder, salesOrderAddForm);
        salesOrder.setOrderedTotalAmount(total(rows));
        stamp(salesOrder, true);
        salesOrderDao.insert(salesOrder);
        for (var row : rows) insert(salesOrder.getId(), row);
        var addressSnapshot = new OrderAddressSnapshotEntity();
        BeanUtils.copyProperties(salesOrderAddForm.getAddress(), addressSnapshot);
        addressSnapshot.setOrderId(salesOrder.getId());
        addressSnapshot.setCustomerId(salesOrder.getCustomerId());
        addressSnapshot.setCreatedAt(OffsetDateTime.now());
        addressSnapshot.setCreatedBy(ScmOperator.current());
        if (addressSnapshot.getAddress() != null && !addressSnapshot.getAddress().isBlank()
            && Objects.equals(addressSnapshot.getAddress(), customer.getAddress())) {
            addressSnapshot.setProvinceCode(customer.getProvinceCode());
            addressSnapshot.setProvinceName(customer.getProvinceName());
            addressSnapshot.setCityCode(customer.getCityCode());
            addressSnapshot.setCityName(customer.getCityName());
            addressSnapshot.setDistrictCode(customer.getDistrictCode());
            addressSnapshot.setDistrictName(customer.getDistrictName());
            if (customer.getGeomCrs() != null) {
                addressSnapshot.setLongitude(customer.getLongitude());
                addressSnapshot.setLatitude(customer.getLatitude());
                addressSnapshot.setGeomCrs(customer.getGeomCrs());
            }
        }
        orderAddressSnapshotDao.insert(addressSnapshot);
        var result = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.CREATE, null, null, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO update(SalesOrderUpdateForm salesOrderUpdateForm) {
        OrderValidator.draft(salesOrderUpdateForm);
        var salesOrder = lock(salesOrderUpdateForm.getOrderId());
        version(salesOrder.getVersion(), salesOrderUpdateForm.getVersion());
        OrderStateMachine.editable(salesOrder.getStatus());
        // Customer and address identity belong to the creation snapshot; editing cannot replace it.
        if (!Objects.equals(salesOrder.getCustomerId(), salesOrderUpdateForm.getCustomerId()))
            throw new ScmBusinessException(ORDER_ORIGINAL_INVALID);
        customerService.requireTradable(salesOrder.getCustomerId());
        validateOriginal(salesOrderUpdateForm);
        var oldAddress = orderAddressSnapshotDao.list(salesOrder.getId()).getFirst();
        if (!Objects.equals(oldAddress.getReceiverName(),
            salesOrderUpdateForm.getAddress().getReceiverName())
                || !Objects.equals(oldAddress.getReceiverPhone(),
                    salesOrderUpdateForm.getAddress().getReceiverPhone())
                        || !Objects.equals(oldAddress.getAddress(),
                            salesOrderUpdateForm.getAddress().getAddress()))
            throw new ScmBusinessException(ORDER_STATE_INVALID);
        var before = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        var existing = salesOrderItemDao.list(salesOrder.getId());
        var requested = materialize(salesOrderUpdateForm);
        var changes = SalesOrderItemChangeSet.between(existing, requested);
        // Remove before insert so a removed SKU may be added again without violating the active unique index.
        for (var row : changes.removed()) removeItem(row);
        for (var row : changes.updated()) {
            row.setOrderId(salesOrder.getId());
            saveItem(row);
        }
        for (var row : changes.inserted()) insert(salesOrder.getId(), row);
        header(salesOrder, salesOrderUpdateForm);
        salesOrder.setOrderedTotalAmount(total(requested));
        save(salesOrder);
        var result = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.UPDATE, null, before, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO submit(OrderVersionForm orderVersionForm, String key) {
        var claim = orderIdempotencyService.claim("ORDER_SUBMIT:" + orderVersionForm.getOrderId(), key,
            orderVersionForm);
        if (claim.replay()) return orderIdempotencyService.replay(claim, SalesOrderDetailVO.class);
        var result = submitOrder(orderVersionForm.getOrderId(), orderVersionForm.getVersion());
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name(),
            result.getOrderId(), result);
        return result;
    }

    private SalesOrderDetailVO submitOrder(Long orderId, Integer expectedVersion) {
        var salesOrder = lock(orderId);
        version(salesOrder.getVersion(), expectedVersion);
        OrderStateMachine.transition(salesOrder.getStatus(), ScmOrderStatusEnum.PENDING.name());
        var before = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        var rows = salesOrderItemDao.list(salesOrder.getId());
        var automatic = rows.stream()
                .filter(orderItem -> !orderItem.getManualPriceOverride())
                .map(SalesOrderItemEntity::getSkuId)
                .toList();
        customerService.requireTradable(salesOrder.getCustomerId());
        var resolved = priceResolver.requireResolvable(salesOrder.getCustomerId(), automatic,
            OffsetDateTime.now()).stream().collect(Collectors.toMap(ResolvedPriceVO::getSkuId,
                Function.identity()));
        var manual = rows.stream().filter(SalesOrderItemEntity::getManualPriceOverride).map(SalesOrderItemEntity::getSkuId).toList();
        var manualResolved = priceResolver.resolve(salesOrder.getCustomerId(), manual, OffsetDateTime.now());
        if (manualResolved.stream().anyMatch(resolvedPrice -> !resolvedPrice.isSellable()))
            throw new ScmBusinessException(com.xsy.scm.pricing.constant.PricingErrorCode.SKU_NOT_SELLABLE);
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
            if (ScmOrderProductTypeEnum.STANDARD.name().equals(row.getProductTypeSnapshot())) {
                row.setActualQuantity(row.getOrderedQuantity());
                row.setActualQuantitySource(ScmOrderQuantitySourceEnum.SYSTEM.name());
            }
            saveItem(row);
        }
        salesOrder.setOrderedTotalAmount(total(rows));
        salesOrder.setStatus(ScmOrderStatusEnum.PENDING.name());
        salesOrder.setSubmittedAt(OffsetDateTime.now());
        save(salesOrder);
        var result = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.SUBMIT, null, before, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO actualQuantity(OrderActualQuantityForm orderActualQuantityForm, String key) {
        var claim = orderIdempotencyService.claim(
                "ORDER_ACTUAL:" + orderActualQuantityForm.getOrderId() + ":" + orderActualQuantityForm.getItemId(),
                key,
                orderActualQuantityForm);
        if (claim.replay()) return orderIdempotencyService.replay(claim, SalesOrderDetailVO.class);
        var salesOrder = lock(orderActualQuantityForm.getOrderId());
        OrderStateMachine.actualQuantity(salesOrder.getStatus());
        var row = salesOrderItemDao.list(salesOrder.getId()).stream()
                .filter(salesOrderItem -> Objects.equals(salesOrderItem.getId(), orderActualQuantityForm.getItemId()))
                .findFirst()
                .orElseThrow(() -> new ScmBusinessException(ORDER_ITEM_NOT_OWNED));
        if (!ScmOrderProductTypeEnum.NON_STANDARD.name().equals(row.getProductTypeSnapshot()))
            throw new ScmBusinessException(ORDER_ACTUAL_NOT_ALLOWED);
        if (!Objects.equals(row.getVersion(), orderActualQuantityForm.getVersion()))
            throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);
        OrderValidator.reason(orderActualQuantityForm.getReason(), ORDER_ACTUAL_REASON_REQUIRED);
        var before = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        row.setActualQuantity(OrderValidator.decimal(orderActualQuantityForm.getActualQuantity(), true));
        row.setActualQuantitySource(ScmOrderQuantitySourceEnum.MANUAL.name());
        row.setActualQuantityReason(orderActualQuantityForm.getReason().trim());
        saveItem(row);
        // Advance the aggregate version too: stale confirm forms must refresh after any item change.
        save(salesOrder);
        var result = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.ACTUAL_QUANTITY,
            orderActualQuantityForm.getReason(), before, result);
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name(),
            salesOrder.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO confirm(OrderVersionForm orderVersionForm, String key) {
        var claim = orderIdempotencyService.claim("ORDER_CONFIRM:" + orderVersionForm.getOrderId(), key,
            orderVersionForm);
        if (claim.replay()) return orderIdempotencyService.replay(claim, SalesOrderDetailVO.class);
        var result = confirmOrder(orderVersionForm.getOrderId(), orderVersionForm.getVersion());
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name(),
            result.getOrderId(), result);
        return result;
    }

    private SalesOrderDetailVO confirmOrder(Long orderId, Integer expectedVersion) {
        var salesOrder = lock(orderId);
        version(salesOrder.getVersion(), expectedVersion);
        OrderStateMachine.transition(salesOrder.getStatus(), ScmOrderStatusEnum.CONFIRMED.name());
        var before = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        var rows = salesOrderItemDao.list(salesOrder.getId());
        for (var row : rows) {
            if (row.getActualQuantity() == null || row.getActualQuantity().signum() <= 0)
                throw new ScmBusinessException(ORDER_ACTUAL_QUANTITY_REQUIRED);
            row.setSettlementLineAmount(OrderAmountCalculator.lineAmount(row.getActualQuantity(), row.getLockedUnitPrice()));
            saveItem(row);
        }
        salesOrder.setSettlementTotalAmount(
                OrderAmountCalculator.orderAmount(
                        rows.stream().map(SalesOrderItemEntity::getSettlementLineAmount).toList()));
        salesOrder.setStatus(ScmOrderStatusEnum.CONFIRMED.name());
        salesOrder.setConfirmedAt(OffsetDateTime.now());
        save(salesOrder);
        // 订单确认不自动预留库存；当前主链是先接单、再采购和收货，预留由后续显式动作完成。
        var result = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.CONFIRM, null, before, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesOrderDetailVO cancel(OrderCancelForm orderCancelForm, String key) {
        var claim = orderIdempotencyService.claim("ORDER_CANCEL:" + orderCancelForm.getOrderId(), key, orderCancelForm);
        if (claim.replay()) return orderIdempotencyService.replay(claim, SalesOrderDetailVO.class);
        var salesOrder = lock(orderCancelForm.getOrderId());
        version(salesOrder.getVersion(), orderCancelForm.getVersion());
        OrderStateMachine.transition(salesOrder.getStatus(), ScmOrderStatusEnum.CANCELLED.name());
        OrderValidator.reason(orderCancelForm.getReason(), ORDER_CANCEL_REASON_REQUIRED);
        var before = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        salesOrder.setStatus(ScmOrderStatusEnum.CANCELLED.name());
        salesOrder.setCancelReason(orderCancelForm.getReason().trim());
        salesOrder.setCancelledAt(OffsetDateTime.now());
        save(salesOrder);
        // 取消时释放该订单的预留（若存在）。当前没有触发点会创建预留，因此通常是空操作；
        // 保留这行是为了让「预留一旦启用」时取消路径自动正确，不需要再改这里。
        inventoryReservationService.releaseBySalesOrder(salesOrder.getId());
        var result = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.CANCEL, orderCancelForm.getReason(), before, result);
        orderIdempotencyService.complete(claim, ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name(),
            salesOrder.getId(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(OrderVersionForm orderVersionForm) {
        var salesOrder = salesOrderDao.lock(orderVersionForm.getOrderId());
        if (salesOrder == null) return;
        version(salesOrder.getVersion(), orderVersionForm.getVersion());
        if (!ScmOrderStatusEnum.DRAFT.name().equals(salesOrder.getStatus())) {
            throw new ScmBusinessException(ORDER_DELETE_STATE_INVALID);
        }
        var before = salesOrderQueryService.detailSnapshot(salesOrder.getId());
        for (var row : salesOrderItemDao.list(salesOrder.getId())) removeItem(row);
        if (salesOrderDao.softDelete(salesOrder.getId(), salesOrder.getVersion(), ScmOperator.current()) != 1)
            throw new ScmBusinessException(VERSION_CONFLICT);
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.UPDATE, "删除草稿", before, Map.of("deleted", true,
            "version", salesOrder.getVersion() + 1));
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(OrderBatchDeleteForm batchDeleteForm) {
        for (var orderVersionForm : batchDeleteForm.getOrders().stream()
                .sorted(Comparator.comparing(OrderVersionForm::getOrderId))
                .toList())
            delete(orderVersionForm);
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
        var salesOrder = lock(orderId);
        if (!ScmOrderStatusEnum.CONFIRMED.name().equals(salesOrder.getStatus())) {
            throw new ScmBusinessException(ORDER_RESERVE_STATE_INVALID);
        }
        var rows = salesOrderItemDao.list(salesOrder.getId());
        inventoryReservationService.reserveForSalesOrder(salesOrder.getId(),
                rows.stream()
                        .map(salesOrderItem -> new InventoryReservationService.OrderReserveLine(
                                salesOrderItem.getId(), salesOrderItem.getSkuId(), salesOrderItem.getActualQuantity()))
                        .toList(),
                OffsetDateTime.now());
        log(salesOrder.getId(), ScmOrderOperationTypeEnum.RESERVE_STOCK, null, null,
            Map.of("reservedLines", rows.size()));
    }

    public SalesOrderEntity lock(Long orderId) {
        var salesOrder = salesOrderDao.lock(orderId);
        if (salesOrder == null) throw new ScmBusinessException(ORDER_NOT_FOUND);
        return salesOrder;
    }

    public static void version(Integer actual, Integer expected) {
        if (!Objects.equals(actual, expected)) throw new ScmBusinessException(VERSION_CONFLICT);
    }

    private void validateOriginal(SalesOrderAddForm salesOrderAddForm) {
        if (salesOrderAddForm.getOriginalOrderId() == null) return;
        var original = salesOrderDao.selectById(salesOrderAddForm.getOriginalOrderId());
        if (original == null || !ScmOrderStatusEnum.CONFIRMED.name().equals(original.getStatus())
            || !Objects.equals(original.getCustomerId(), salesOrderAddForm.getCustomerId()))
            throw new ScmBusinessException(ORDER_ORIGINAL_INVALID);
    }

    private List<SalesOrderItemEntity> materialize(SalesOrderAddForm salesOrderAddForm) {
        var ids = salesOrderAddForm.getItems().stream().map(SalesOrderItemForm::getSkuId).toList();
        var products = productSkuOptionDao.selectByIds(ids).stream()
                .collect(Collectors.toMap(skuOption -> skuOption.getSkuId(), Function.identity()));
        var spuIds = products.values().stream().map(skuOption -> skuOption.getSpuId()).distinct().toList();
        var codes = spuIds.isEmpty()
                ? Map.<Long, String>of()
                : productSpuDao.selectBatchIds(spuIds).stream()
                        .collect(Collectors.toMap(
                                productSpu -> productSpu.getId(), productSpu -> productSpu.getSpuCode()));
        var resolved = priceResolver.resolve(salesOrderAddForm.getCustomerId(), ids,
            OffsetDateTime.now()).stream().collect(Collectors.toMap(ResolvedPriceVO::getSkuId,
                Function.identity()));
        return salesOrderAddForm.getItems().stream().map(orderItemForm -> {
            var productSkuOption = products.get(orderItemForm.getSkuId());
            return OrderSnapshotFactory.item(orderItemForm, productSkuOption,
                    productSkuOption == null ? null : codes.get(productSkuOption.getSpuId()),
                        resolved.get(orderItemForm.getSkuId()));
        }).toList();
    }

    private void header(SalesOrderEntity salesOrder, SalesOrderAddForm salesOrderAddForm) {
        salesOrder.setOrderSource(salesOrderAddForm.getOrderSource());
        salesOrder.setOriginalOrderId(salesOrderAddForm.getOriginalOrderId());
        salesOrder.setSupplementReason(OrderValidator.trim(salesOrderAddForm.getSupplementReason()));
        salesOrder.setRemark(OrderValidator.trim(salesOrderAddForm.getRemark()));
        salesOrder.setExpectDeliveryTime(salesOrderAddForm.getExpectDeliveryTime());
    }

    private BigDecimal total(List<SalesOrderItemEntity> rows) {
        return OrderAmountCalculator.orderAmount(rows.stream().map(SalesOrderItemEntity::getOrderedLineAmount).toList());
    }

    private void insert(Long orderId, SalesOrderItemEntity salesOrderItem) {
        salesOrderItem.setOrderId(orderId);
        salesOrderItem.setVersion(0);
        salesOrderItem.setCreatedAt(OffsetDateTime.now());
        salesOrderItem.setCreatedBy(ScmOperator.current());
        salesOrderItem.setUpdatedAt(salesOrderItem.getCreatedAt());
        salesOrderItem.setUpdatedBy(salesOrderItem.getCreatedBy());
        salesOrderItemDao.insert(salesOrderItem);
    }

    private void stamp(SalesOrderEntity salesOrder, boolean creating) {
        salesOrder.setUpdatedAt(OffsetDateTime.now());
        salesOrder.setUpdatedBy(ScmOperator.current());
        if (creating) {
            salesOrder.setCreatedAt(salesOrder.getUpdatedAt());
            salesOrder.setCreatedBy(salesOrder.getUpdatedBy());
        }
    }

    private void save(SalesOrderEntity salesOrder) {
        stamp(salesOrder, false);
        if (salesOrderDao.updateById(salesOrder) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
    }

    private void saveItem(SalesOrderItemEntity row) {
        row.setUpdatedAt(OffsetDateTime.now());
        row.setUpdatedBy(ScmOperator.current());
        if (salesOrderItemDao.updateById(row) != 1) throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);
    }

    private void removeItem(SalesOrderItemEntity row) {
        if (salesOrderItemDao.softDelete(row.getId(), row.getVersion(), ScmOperator.current()) != 1)
            throw new ScmBusinessException(ORDER_ITEM_VERSION_CONFLICT);
    }

    private void log(Long orderId, ScmOrderOperationTypeEnum operationType, String reason, Object before,
        Object after) {
        orderLogs.record(orderId, operationType, reason, before, after);
    }
}
