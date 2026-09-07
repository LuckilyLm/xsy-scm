package com.xianshuyuan.scm.purchase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.order.service.IdempotencyService;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import com.xianshuyuan.scm.product.mapper.ProductSpuMapper;
import com.xianshuyuan.scm.purchase.dto.PurchaseOrderCancelRequest;
import com.xianshuyuan.scm.purchase.dto.PurchaseOrderItemRequest;
import com.xianshuyuan.scm.purchase.dto.PurchaseOrderSaveRequest;
import com.xianshuyuan.scm.purchase.dto.PurchaseOrderVersionRequest;
import com.xianshuyuan.scm.purchase.entity.PurchaseDemandAllocationEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseDemandEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseDemandStatus;
import com.xianshuyuan.scm.purchase.entity.PurchaseOperationLogEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderItemEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderStatus;
import com.xianshuyuan.scm.purchase.mapper.PurchaseDemandAllocationMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseDemandMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseOperationLogMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseOrderItemMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseOrderMapper;
import com.xianshuyuan.scm.purchase.vo.PurchaseDemandAllocationResponse;
import com.xianshuyuan.scm.purchase.vo.PurchaseOperationLogResponse;
import com.xianshuyuan.scm.purchase.vo.PurchaseOrderItemResponse;
import com.xianshuyuan.scm.purchase.vo.PurchaseOrderResponse;
import com.xianshuyuan.scm.supplier.entity.SupplierSkuEntity;
import com.xianshuyuan.scm.supplier.service.SupplierService;
import com.xianshuyuan.scm.supplier.service.SupplierSkuService;
import com.xianshuyuan.scm.supplier.service.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class PurchaseOrderService {
    private final PurchaseOrderMapper orders;
    private final PurchaseOrderItemMapper orderItems;
    private final PurchaseOperationLogMapper logs;
    private final PurchaseDemandAllocationMapper allocations;
    private final SupplierService suppliers;
    private final WarehouseService warehouses;
    private final SupplierSkuService supplierSkus;
    private final ProductSkuMapper skus;
    private final ProductSpuMapper spus;
    private final PurchaseDemandMapper demands;
    private final PurchaseOrderNumberGenerator numbers;
    private final IdempotencyService idempotency;
    private final ObjectMapper json;

    public PurchaseOrderService(
            PurchaseOrderMapper orders,
            PurchaseOrderItemMapper orderItems,
            PurchaseOperationLogMapper logs,
            PurchaseDemandAllocationMapper allocations,
            SupplierService suppliers,
            WarehouseService warehouses,
            SupplierSkuService supplierSkus,
            ProductSkuMapper skus,
            ProductSpuMapper spus,
            PurchaseDemandMapper demands,
            PurchaseOrderNumberGenerator numbers,
            IdempotencyService idempotency,
            ObjectMapper json
    ) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.logs = logs;
        this.allocations = allocations;
        this.suppliers = suppliers;
        this.warehouses = warehouses;
        this.supplierSkus = supplierSkus;
        this.skus = skus;
        this.spus = spus;
        this.demands = demands;
        this.numbers = numbers;
        this.idempotency = idempotency;
        this.json = json;
    }

    @Transactional
    public long create(PurchaseOrderSaveRequest request, String key) {
        var claim = idempotency.claim("PURCHASE_ORDER_CREATE", key, request);
        if (claim.replay()) {
            return idempotency.replay(claim, Long.class);
        }
        var supplier = suppliers.requireEnabledSupplier(request.supplierId());
        var warehouse = warehouses.requireEnabledWarehouse(request.warehouseId());
        var order = new PurchaseOrderEntity();
        order.setOrderNo(numbers.next());
        order.setSupplierId(supplier.getId());
        order.setSupplierCodeSnapshot(supplier.getSupplierCode());
        order.setSupplierNameSnapshot(supplier.getName());
        order.setWarehouseId(warehouse.getId());
        order.setWarehouseCodeSnapshot(warehouse.getWarehouseCode());
        order.setWarehouseNameSnapshot(warehouse.getName());
        order.setPurchaserId(request.purchaserId());
        order.setPlannedArrivalDate(request.plannedArrivalDate());
        order.setRemark(request.remark());
        order.setStatus(PurchaseOrderStatus.DRAFT);
        order.setTotalAmount(total(request.items()));
        order.setVersion(0);
        order.setDeleted(false);
        order.setCreatedBy("SYSTEM");
        orders.insert(order);
        persistItemsWithLockedDemands(
                order.getId(), request.items(), supplier.getId(), warehouse.getId());
        log(order, "CREATE", null);
        idempotency.complete(claim, "PURCHASE_ORDER", order.getId(), order.getId());
        return order.getId();
    }

    @Transactional
    public void update(long id, PurchaseOrderSaveRequest request) {
        var order = require(id);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new BusinessException(PurchaseOrderErrorCodes.INVALID_STATE);
        }
        if (!Objects.equals(order.getVersion(), request.version())) {
            throw new BusinessException(PurchaseOrderErrorCodes.VERSION_CONFLICT);
        }
        var supplier = suppliers.requireEnabledSupplier(request.supplierId());
        var warehouse = warehouses.requireEnabledWarehouse(request.warehouseId());
        order.setSupplierId(supplier.getId());
        order.setSupplierCodeSnapshot(supplier.getSupplierCode());
        order.setSupplierNameSnapshot(supplier.getName());
        order.setWarehouseId(warehouse.getId());
        order.setWarehouseCodeSnapshot(warehouse.getWarehouseCode());
        order.setWarehouseNameSnapshot(warehouse.getName());
        order.setPurchaserId(request.purchaserId());
        order.setPlannedArrivalDate(request.plannedArrivalDate());
        order.setRemark(request.remark());
        order.setTotalAmount(total(request.items()));
        order.setVersion(request.version());
        if (orders.updateById(order) != 1) {
            throw new BusinessException(PurchaseOrderErrorCodes.VERSION_CONFLICT);
        }
        reconcileItems(id, request.items(), supplier.getId(), warehouse.getId());
        log(order, "UPDATE", null);
    }

    @Transactional
    public void submit(long id, int version, String key) {
        var claim = idempotency.claim(
                "PURCHASE_ORDER_SUBMIT:" + id, key, new PurchaseOrderVersionRequest(version));
        if (claim.replay()) {
            return;
        }
        var order = require(id);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new BusinessException(PurchaseOrderErrorCodes.INVALID_STATE);
        }
        if (!Objects.equals(order.getVersion(), version)) {
            throw new BusinessException(PurchaseOrderErrorCodes.VERSION_CONFLICT);
        }
        order.setStatus(PurchaseOrderStatus.SUBMITTED);
        order.setSubmittedAt(OffsetDateTime.now());
        order.setVersion(version);
        if (orders.updateById(order) != 1) {
            throw new BusinessException(PurchaseOrderErrorCodes.VERSION_CONFLICT);
        }
        log(order, "SUBMIT", null);
        idempotency.complete(claim, "PURCHASE_ORDER", id, id);
    }

    @Transactional
    public void cancel(long id, PurchaseOrderCancelRequest request, String key) {
        var claim = idempotency.claim("PURCHASE_ORDER_CANCEL:" + id, key, request);
        if (claim.replay()) {
            return;
        }
        var order = require(id);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT
                && order.getStatus() != PurchaseOrderStatus.SUBMITTED) {
            throw new BusinessException(PurchaseOrderErrorCodes.INVALID_STATE);
        }
        if (!Objects.equals(order.getVersion(), request.version())) {
            throw new BusinessException(PurchaseOrderErrorCodes.VERSION_CONFLICT);
        }
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        order.setCancelReason(request.reason());
        order.setCancelledAt(OffsetDateTime.now());
        order.setVersion(request.version());
        if (orders.updateById(order) != 1) {
            throw new BusinessException(PurchaseOrderErrorCodes.VERSION_CONFLICT);
        }
        log(order, "CANCEL", request.reason());
        idempotency.complete(claim, "PURCHASE_ORDER", id, id);
    }

    private void log(PurchaseOrderEntity order, String type, String reason) {
        var entry = new PurchaseOperationLogEntity();
        entry.setPurchaseOrderId(order.getId());
        entry.setOperationType(type);
        entry.setOperator("SYSTEM");
        entry.setCreatedBy("SYSTEM");
        entry.setReason(reason);
        entry.setAfterData(JsonNodeFactory.instance.objectNode()
                .put("status", order.getStatus().name())
                .put("version", order.getVersion()));
        logs.insert(entry);
    }

    private void persistItemsWithLockedDemands(
            long orderId,
            List<PurchaseOrderItemRequest> rows,
            long supplierId,
            long warehouseId
    ) {
        Map<Long, PurchaseDemandEntity> lockedDemands = lockAndValidateDemands(
                rows, supplierId, warehouseId);
        persistItems(orderId, rows, supplierId, warehouseId, lockedDemands);
    }

    private void persistItems(
            long orderId,
            List<PurchaseOrderItemRequest> rows,
            long supplierId,
            long warehouseId,
            Map<Long, PurchaseDemandEntity> lockedDemands
    ) {
        int sortOrder = 0;
        for (var row : rows) {
            SupplierSkuEntity configuration = supplierSkus.requireEnabledForPurchasing(
                    supplierId, row.skuId());
            ProductSkuEntity sku = skus.selectOrderableByIds(List.of(row.skuId())).stream()
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(PurchaseOrderErrorCodes.INVALID_STATE));
            var spu = spus.selectById(sku.getSpuId());
            if (spu == null || spu.getSpuCode() == null) {
                throw new BusinessException(PurchaseOrderErrorCodes.INVALID_STATE);
            }
            var quantity = parseQuantity(row.quantity());
            var price = parsePrice(row.price());
            var item = new PurchaseOrderItemEntity();
            item.setPurchaseOrderId(orderId);
            item.setSpuId(sku.getSpuId());
            item.setSkuId(sku.getId());
            item.setSpuCodeSnapshot(spu.getSpuCode());
            item.setProductNameSnapshot(sku.getProductName());
            item.setSkuCodeSnapshot(sku.getSkuCode());
            item.setSkuNameSnapshot(sku.getProductName());
            item.setSpecValuesSnapshot(sku.getSpecValues() == null ? Map.of() : sku.getSpecValues());
            item.setPurchaseUnitSnapshot(configuration.getPurchaseUnit());
            item.setProductTypeSnapshot(sku.getProductType());
            item.setPlannedQuantity(quantity);
            item.setReceivedQuantity(BigDecimal.ZERO.setScale(4));
            item.setPurchasePrice(price.setScale(4, RoundingMode.HALF_UP));
            item.setLineAmount(quantity.multiply(price).setScale(4, RoundingMode.HALF_UP));
            item.setSortOrder(sortOrder++);
            item.setVersion(0);
            item.setDeleted(false);
            item.setCreatedBy("SYSTEM");
            orderItems.insert(item);
            allocateDemand(
                    item,
                    lockedDemands.get(row.demandId()),
                    quantity,
                    supplierId,
                    warehouseId);
        }
    }

    private Map<Long, PurchaseDemandEntity> lockAndValidateDemands(
            List<PurchaseOrderItemRequest> rows,
            long supplierId,
            long warehouseId
    ) {
        Map<Long, BigDecimal> requestedByDemand = new HashMap<>();
        Map<Long, Long> skuByDemand = new HashMap<>();
        for (var row : rows) {
            BigDecimal quantity = parseQuantity(row.quantity());
            requestedByDemand.merge(row.demandId(), quantity, BigDecimal::add);
            Long previousSku = skuByDemand.putIfAbsent(row.demandId(), row.skuId());
            if (previousSku != null && !Objects.equals(previousSku, row.skuId())) {
                throw new BusinessException(PurchaseOrderErrorCodes.INVALID_STATE);
            }
        }

        Map<Long, PurchaseDemandEntity> result = new HashMap<>();
        requestedByDemand.keySet().stream().sorted().forEach(demandId -> {
            PurchaseDemandEntity demand = requireDemand(demandId, skuByDemand.get(demandId));
            BigDecimal requested = requestedByDemand.get(demandId);
            if (demand.getAllocatedQuantity().add(requested)
                    .compareTo(demand.getRequiredQuantity()) > 0) {
                throw new BusinessException(PurchaseDemandErrorCodes.OVER_ALLOCATED);
            }
            if (demand.getAllocatedQuantity().signum() > 0
                    && (!Objects.equals(demand.getSupplierId(), supplierId)
                    || !Objects.equals(demand.getWarehouseId(), warehouseId))) {
                throw new BusinessException(PurchaseDemandErrorCodes.ALLOCATION_CONFLICT);
            }
            result.put(demandId, demand);
        });
        return result;
    }

    private BigDecimal parseQuantity(String value) {
        try {
            BigDecimal quantity = new BigDecimal(value);
            if (quantity.signum() <= 0 || quantity.scale() > 4 || quantity.precision() - quantity.scale() > 14) {
                throw new NumberFormatException();
            }
            return quantity;
        } catch (NumberFormatException error) {
            throw new BusinessException(PurchaseOrderErrorCodes.INVALID_QUANTITY);
        }
    }

    private void allocateDemand(
            PurchaseOrderItemEntity item,
            PurchaseDemandEntity demand,
            BigDecimal quantity,
            long supplierId,
            long warehouseId
    ) {
        if (demand == null || !Objects.equals(demand.getSkuId(), item.getSkuId())) {
            throw new BusinessException(PurchaseOrderErrorCodes.INVALID_STATE);
        }
        long demandId = demand.getId();
        var existing = allocations.selectActiveByOrderItemAndDemandForUpdate(item.getId(), demandId);
        if (existing != null) {
            if (existing.getAllocatedQuantity().compareTo(quantity) == 0) {
                return;
            }
            throw new BusinessException(PurchaseDemandErrorCodes.ALLOCATION_CONFLICT);
        }
        applyDemandDelta(demand, quantity, supplierId, warehouseId);
        var allocation = new PurchaseDemandAllocationEntity();
        allocation.setPurchaseDemandId(demandId);
        allocation.setPurchaseOrderItemId(item.getId());
        allocation.setSalesOrderId(demand.getSalesOrderId());
        allocation.setSalesOrderItemId(demand.getSalesOrderItemId());
        allocation.setSkuId(demand.getSkuId());
        allocation.setAllocatedQuantity(quantity);
        allocation.setDemandSnapshot(JsonNodeFactory.instance.objectNode().put("demandId", demandId));
        allocation.setVersion(0);
        allocation.setDeleted(false);
        allocation.setCreatedBy("SYSTEM");
        allocations.insert(allocation);
        updateDemand(demand);
    }

    private PurchaseDemandEntity requireDemand(long demandId, long skuId) {
        var demand = demands.selectActiveByIdForUpdate(demandId);
        if (demand == null || !Objects.equals(demand.getSkuId(), skuId)) {
            throw new BusinessException(PurchaseOrderErrorCodes.INVALID_STATE);
        }
        return demand;
    }

    private void applyDemandDelta(
            PurchaseDemandEntity demand,
            BigDecimal delta,
            long supplierId,
            long warehouseId
    ) {
        BigDecimal allocated = demand.getAllocatedQuantity().add(delta);
        if (allocated.signum() < 0 || allocated.compareTo(demand.getRequiredQuantity()) > 0) {
            throw new BusinessException(PurchaseOrderErrorCodes.INVALID_STATE);
        }
        demand.setAllocatedQuantity(allocated);
        if (allocated.signum() == 0) {
            demand.setSupplierId(null);
            demand.setWarehouseId(null);
            demand.setStatus(PurchaseDemandStatus.PENDING);
        } else {
            demand.setSupplierId(supplierId);
            demand.setWarehouseId(warehouseId);
            demand.setStatus(allocated.compareTo(demand.getRequiredQuantity()) == 0
                    ? PurchaseDemandStatus.ALLOCATED
                    : PurchaseDemandStatus.PARTIALLY_ALLOCATED);
        }
    }

    private void updateDemand(PurchaseDemandEntity demand) {
        if (demands.updateById(demand) != 1) {
            throw new BusinessException(PurchaseOrderErrorCodes.VERSION_CONFLICT);
        }
    }

    private void reconcileItems(
            long orderId,
            List<PurchaseOrderItemRequest> requests,
            long supplierId,
            long warehouseId
    ) {
        var existingItems = orderItems.selectActiveByOrderIdForUpdate(orderId);
        var existingById = new HashMap<Long, PurchaseOrderItemEntity>();
        existingItems.forEach(item -> existingById.put(item.getId(), item));

        var queriedAllocations = allocations.selectActiveByOrderIdForUpdate(orderId);
        var existingAllocations = new java.util.ArrayList<PurchaseDemandAllocationEntity>();
        if (queriedAllocations != null) {
            existingAllocations.addAll(queriedAllocations);
        }
        if (existingAllocations.isEmpty()) {
            for (var item : existingItems) {
                var allocation = allocations.selectActiveByOrderItemIdForUpdate(item.getId());
                if (allocation != null) {
                    existingAllocations.add(allocation);
                }
            }
        }
        var allocationByItemId = new HashMap<Long, PurchaseDemandAllocationEntity>();
        var oldTotals = new HashMap<Long, BigDecimal>();
        for (var allocation : existingAllocations) {
            allocationByItemId.put(allocation.getPurchaseOrderItemId(), allocation);
            oldTotals.merge(
                    allocation.getPurchaseDemandId(),
                    allocation.getAllocatedQuantity(),
                    BigDecimal::add);
        }

        var retainedIds = new HashSet<Long>();
        var seenIds = new HashSet<Long>();
        var finalTotals = new HashMap<Long, BigDecimal>();
        var skuByDemand = new HashMap<Long, Long>();
        var quantities = new LinkedHashMap<PurchaseOrderItemRequest, BigDecimal>();
        for (var request : requests) {
            BigDecimal quantity = parseQuantity(request.quantity());
            parsePrice(request.price());
            quantities.put(request, quantity);
            finalTotals.merge(request.demandId(), quantity, BigDecimal::add);
            Long previousSku = skuByDemand.putIfAbsent(request.demandId(), request.skuId());
            if (previousSku != null && !Objects.equals(previousSku, request.skuId())) {
                throw new BusinessException(PurchaseOrderErrorCodes.INVALID_STATE);
            }
            if (request.id() == null) {
                continue;
            }
            if (!seenIds.add(request.id())) {
                throw new BusinessException(PurchaseOrderErrorCodes.INVALID_STATE);
            }
            var item = existingById.get(request.id());
            if (item == null || !Objects.equals(item.getSkuId(), request.skuId())) {
                throw new BusinessException(PurchaseOrderErrorCodes.INVALID_STATE);
            }
            if (request.version() == null || !Objects.equals(item.getVersion(), request.version())) {
                throw new BusinessException(PurchaseOrderErrorCodes.VERSION_CONFLICT);
            }
            var allocation = allocationByItemId.get(item.getId());
            if (allocation != null
                    && !Objects.equals(allocation.getPurchaseDemandId(), request.demandId())) {
                throw new BusinessException(PurchaseOrderErrorCodes.DEMAND_REPLACEMENT_NOT_ALLOWED);
            }
            retainedIds.add(item.getId());
        }
        for (var item : existingItems) {
            if (!retainedIds.contains(item.getId())
                    && item.getReceivedQuantity() != null
                    && item.getReceivedQuantity().signum() > 0) {
                throw new BusinessException(PurchaseOrderErrorCodes.INVALID_STATE);
            }
        }

        var demandIds = new HashSet<Long>(oldTotals.keySet());
        demandIds.addAll(finalTotals.keySet());
        var lockedDemands = new HashMap<Long, PurchaseDemandEntity>();
        demandIds.stream().sorted().forEach(demandId -> {
            Long skuId = skuByDemand.get(demandId);
            if (skuId == null) {
                var oldAllocation = existingAllocations.stream()
                        .filter(row -> Objects.equals(row.getPurchaseDemandId(), demandId))
                        .findFirst()
                        .orElseThrow();
                skuId = existingById.get(oldAllocation.getPurchaseOrderItemId()).getSkuId();
            }
            PurchaseDemandEntity demand = requireDemand(demandId, skuId);
            BigDecimal otherAllocated = demand.getAllocatedQuantity()
                    .subtract(oldTotals.getOrDefault(demandId, BigDecimal.ZERO));
            BigDecimal finalAllocated = otherAllocated
                    .add(finalTotals.getOrDefault(demandId, BigDecimal.ZERO));
            if (otherAllocated.signum() < 0
                    || finalAllocated.compareTo(demand.getRequiredQuantity()) > 0) {
                throw new BusinessException(PurchaseDemandErrorCodes.OVER_ALLOCATED);
            }
            if (finalAllocated.signum() > 0
                    && (!Objects.equals(demand.getSupplierId(), supplierId)
                    || !Objects.equals(demand.getWarehouseId(), warehouseId))
                    && otherAllocated.signum() > 0) {
                throw new BusinessException(PurchaseDemandErrorCodes.ALLOCATION_CONFLICT);
            }
            lockedDemands.put(demandId, demand);
        });

        int sortOrder = 0;
        for (var entry : quantities.entrySet()) {
            var request = entry.getKey();
            var item = request.id() == null ? null : existingById.get(request.id());
            if (item == null) {
                item = createItem(orderId, request, entry.getValue(), supplierId, sortOrder);
                var allocation = newAllocation(item, lockedDemands.get(request.demandId()), entry.getValue());
                allocations.insert(allocation);
            } else {
                SupplierSkuEntity configuration = supplierSkus.requireEnabledForPurchasing(
                        supplierId, request.skuId());
                BigDecimal price = parsePrice(request.price());
                item.setPlannedQuantity(entry.getValue());
                item.setPurchasePrice(price.setScale(4, RoundingMode.HALF_UP));
                item.setLineAmount(entry.getValue().multiply(price).setScale(4, RoundingMode.HALF_UP));
                item.setPurchaseUnitSnapshot(configuration.getPurchaseUnit());
                item.setSortOrder(sortOrder);
                item.setVersion(request.version());
                if (orderItems.updateById(item) != 1) {
                    throw new BusinessException(PurchaseOrderErrorCodes.VERSION_CONFLICT);
                }
                var allocation = allocationByItemId.get(item.getId());
                if (allocation == null) {
                    allocation = newAllocation(
                            item, lockedDemands.get(request.demandId()), entry.getValue());
                    allocations.insert(allocation);
                } else {
                    allocation.setAllocatedQuantity(entry.getValue());
                    if (allocations.updateById(allocation) != 1) {
                        throw new BusinessException(PurchaseOrderErrorCodes.VERSION_CONFLICT);
                    }
                }
            }
            sortOrder++;
        }

        for (var item : existingItems) {
            if (!retainedIds.contains(item.getId())) {
                var allocation = allocationByItemId.get(item.getId());
                if (allocation != null) {
                    if (allocations.softDeleteWithVersion(allocation.getId(), allocation.getVersion()) != 1) {
                        throw new BusinessException(PurchaseOrderErrorCodes.VERSION_CONFLICT);
                    }
                    allocation.setDeleted(true);
                }
                if (orderItems.softDeleteOwnedWithVersion(
                        orderId, item.getId(), item.getVersion()) != 1) {
                    throw new BusinessException(PurchaseOrderErrorCodes.VERSION_CONFLICT);
                }
            }
        }

        for (Long demandId : demandIds.stream().sorted().toList()) {
            var demand = lockedDemands.get(demandId);
            BigDecimal finalAllocated = demand.getAllocatedQuantity()
                    .subtract(oldTotals.getOrDefault(demandId, BigDecimal.ZERO))
                    .add(finalTotals.getOrDefault(demandId, BigDecimal.ZERO));
            demand.setAllocatedQuantity(finalAllocated);
            if (finalAllocated.signum() == 0) {
                demand.setSupplierId(null);
                demand.setWarehouseId(null);
                demand.setStatus(PurchaseDemandStatus.PENDING);
            } else {
                demand.setSupplierId(supplierId);
                demand.setWarehouseId(warehouseId);
                demand.setStatus(finalAllocated.compareTo(demand.getRequiredQuantity()) == 0
                        ? PurchaseDemandStatus.ALLOCATED
                        : PurchaseDemandStatus.PARTIALLY_ALLOCATED);
            }
            updateDemand(demand);
        }
    }

    private PurchaseOrderItemEntity createItem(
            long orderId,
            PurchaseOrderItemRequest request,
            BigDecimal quantity,
            long supplierId,
            int sortOrder
    ) {
        SupplierSkuEntity configuration = supplierSkus.requireEnabledForPurchasing(
                supplierId, request.skuId());
        ProductSkuEntity sku = skus.selectOrderableByIds(List.of(request.skuId())).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(PurchaseOrderErrorCodes.INVALID_STATE));
        var spu = spus.selectById(sku.getSpuId());
        if (spu == null || spu.getSpuCode() == null) {
            throw new BusinessException(PurchaseOrderErrorCodes.INVALID_STATE);
        }
        BigDecimal price = parsePrice(request.price());
        var item = new PurchaseOrderItemEntity();
        item.setPurchaseOrderId(orderId);
        item.setSpuId(sku.getSpuId());
        item.setSkuId(sku.getId());
        item.setSpuCodeSnapshot(spu.getSpuCode());
        item.setProductNameSnapshot(sku.getProductName());
        item.setSkuCodeSnapshot(sku.getSkuCode());
        item.setSkuNameSnapshot(sku.getProductName());
        item.setSpecValuesSnapshot(sku.getSpecValues() == null ? Map.of() : sku.getSpecValues());
        item.setPurchaseUnitSnapshot(configuration.getPurchaseUnit());
        item.setProductTypeSnapshot(sku.getProductType());
        item.setPlannedQuantity(quantity);
        item.setReceivedQuantity(BigDecimal.ZERO.setScale(4));
        item.setPurchasePrice(price.setScale(4, RoundingMode.HALF_UP));
        item.setLineAmount(quantity.multiply(price).setScale(4, RoundingMode.HALF_UP));
        item.setSortOrder(sortOrder);
        item.setVersion(0);
        item.setDeleted(false);
        item.setCreatedBy("SYSTEM");
        orderItems.insert(item);
        return item;
    }

    private PurchaseDemandAllocationEntity newAllocation(
            PurchaseOrderItemEntity item,
            PurchaseDemandEntity demand,
            BigDecimal quantity
    ) {
        var allocation = new PurchaseDemandAllocationEntity();
        allocation.setPurchaseDemandId(demand.getId());
        allocation.setPurchaseOrderItemId(item.getId());
        allocation.setSalesOrderId(demand.getSalesOrderId());
        allocation.setSalesOrderItemId(demand.getSalesOrderItemId());
        allocation.setSkuId(demand.getSkuId());
        allocation.setAllocatedQuantity(quantity);
        allocation.setDemandSnapshot(JsonNodeFactory.instance.objectNode().put("demandId", demand.getId()));
        allocation.setVersion(0);
        allocation.setDeleted(false);
        allocation.setCreatedBy("SYSTEM");
        return allocation;
    }

    private BigDecimal parsePrice(String value) {
        try {
            BigDecimal price = new BigDecimal(value);
            if (price.signum() < 0 || price.scale() > 4 || price.precision() - price.scale() > 14) {
                throw new NumberFormatException();
            }
            return price;
        } catch (NumberFormatException error) {
            throw new BusinessException(PurchaseOrderErrorCodes.INVALID_QUANTITY);
        }
    }

    public PurchaseOrderResponse detail(long id) {
        var order = require(id);
        var rows = orderItems.selectActiveByOrderId(id);
        var allocationResponses = allocations.selectActiveByOrderId(id).stream()
                .map(allocation -> new PurchaseDemandAllocationResponse(
                        allocation.getId(),
                        allocation.getPurchaseDemandId(),
                        allocation.getPurchaseOrderItemId(),
                        allocation.getSalesOrderId(),
                        allocation.getSalesOrderItemId(),
                        allocation.getSkuId(),
                        allocation.getAllocatedQuantity(),
                        allocation.getDemandSnapshot(),
                        allocation.getVersion()))
                .toList();
        var logResponses = logs.selectByOrderId(id).stream()
                .map(entry -> new PurchaseOperationLogResponse(
                        entry.getId(),
                        entry.getOperationType(),
                        entry.getOperator(),
                        entry.getReason(),
                        entry.getBeforeData(),
                        entry.getAfterData(),
                        entry.getCreatedAt()))
                .toList();
        var itemResponses = rows.stream()
                .map(item -> new PurchaseOrderItemResponse(
                        item.getId(),
                        item.getVersion(),
                        item.getSpuId(),
                        item.getSkuId(),
                        item.getSpuCodeSnapshot(),
                        item.getProductNameSnapshot(),
                        item.getSkuCodeSnapshot(),
                        item.getSkuNameSnapshot(),
                        item.getSpecValuesSnapshot(),
                        item.getPurchaseUnitSnapshot(),
                        item.getProductTypeSnapshot(),
                        item.getPlannedQuantity(),
                        item.getReceivedQuantity(),
                        item.getPurchasePrice(),
                        item.getLineAmount()))
                .toList();
        return new PurchaseOrderResponse(
                order.getId(),
                order.getVersion(),
                order.getOrderNo(),
                order.getSupplierId(),
                order.getSupplierCodeSnapshot(),
                order.getSupplierNameSnapshot(),
                order.getWarehouseId(),
                order.getWarehouseCodeSnapshot(),
                order.getWarehouseNameSnapshot(),
                order.getPurchaserId(),
                order.getPlannedArrivalDate(),
                order.getRemark(),
                order.getStatus(),
                decimal(order.getTotalAmount()),
                order.getSubmittedAt(),
                order.getCancelledAt(),
                order.getCancelReason(),
                itemResponses,
                allocationResponses,
                logResponses);
    }

    public List<PurchaseOrderResponse> listResponses() {
        return orders.selectActiveList().stream()
                .map(order -> detail(order.getId()))
                .toList();
    }

    public List<PurchaseOrderItemEntity> items(long id) {
        require(id);
        return orderItems.selectActiveByOrderId(id);
    }

    public List<PurchaseOperationLogEntity> logs(long id) {
        require(id);
        return logs.selectByOrderId(id);
    }

    private BigDecimal total(List<PurchaseOrderItemRequest> rows) {
        return rows.stream()
                .map(item -> parseQuantity(item.quantity()).multiply(parsePrice(item.price())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
    }

    public PurchaseOrderEntity require(long id) {
        var order = orders.selectActiveById(id);
        if (order == null) {
            throw new BusinessException(PurchaseOrderErrorCodes.NOT_FOUND);
        }
        return order;
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    public List<PurchaseOrderEntity> list() {
        return orders.selectActiveList();
    }
}
