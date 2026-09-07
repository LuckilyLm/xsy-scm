package com.xianshuyuan.scm.purchase.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.order.entity.OrderStatus;
import com.xianshuyuan.scm.order.mapper.SalesOrderItemMapper;
import com.xianshuyuan.scm.order.mapper.SalesOrderMapper;
import com.xianshuyuan.scm.order.service.IdempotencyService;
import com.xianshuyuan.scm.purchase.dto.PurchaseDemandAllocationRequest;
import com.xianshuyuan.scm.purchase.dto.PurchaseDemandGenerateRequest;
import com.xianshuyuan.scm.purchase.entity.PurchaseDemandAllocationEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseDemandEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseDemandStatus;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderStatus;
import com.xianshuyuan.scm.purchase.mapper.PurchaseDemandAllocationMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseDemandMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseOrderItemMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseOrderMapper;
import com.xianshuyuan.scm.supplier.service.SupplierService;
import com.xianshuyuan.scm.supplier.service.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class PurchaseDemandService {
    private final PurchaseDemandMapper demands;
    private final SalesOrderMapper orders;
    private final SalesOrderItemMapper items;
    private final SupplierService suppliers;
    private final WarehouseService warehouses;
    private final PurchaseDemandAllocationMapper allocations;
    private final PurchaseOrderMapper purchaseOrders;
    private final IdempotencyService idempotency;
    private final PurchaseOrderItemMapper orderItems;

    public PurchaseDemandService(
            PurchaseDemandMapper demands,
            SalesOrderMapper orders,
            SalesOrderItemMapper items,
            SupplierService suppliers,
            WarehouseService warehouses,
            PurchaseDemandAllocationMapper allocations,
            PurchaseOrderItemMapper orderItems,
            PurchaseOrderMapper purchaseOrders,
            IdempotencyService idempotency
    ) {
        this.demands = demands;
        this.orders = orders;
        this.items = items;
        this.suppliers = suppliers;
        this.warehouses = warehouses;
        this.allocations = allocations;
        this.orderItems = orderItems;
        this.purchaseOrders = purchaseOrders;
        this.idempotency = idempotency;
    }

    @Transactional
    public List<Long> generate(PurchaseDemandGenerateRequest request, String key) {
        var claim = idempotency.claim("PURCHASE_DEMAND_GENERATE", key, request);
        if (claim.replay()) {
            return idempotency.replay(claim, List.class);
        }
        List<Long> result = generateInternal(request);
        idempotency.complete(claim, "PURCHASE_DEMAND_GENERATE",
                result.isEmpty() ? 0 : result.getFirst(), result);
        return result;
    }

    private List<Long> generateInternal(PurchaseDemandGenerateRequest request) {
        var result = new ArrayList<Long>();
        for (Long orderId : request.salesOrderIds().stream().distinct().sorted().toList()) {
            var order = orders.selectActiveByIdForUpdate(orderId);
            if (order == null || order.getStatus() != OrderStatus.CONFIRMED) {
                throw new BusinessException(PurchaseDemandErrorCodes.SOURCE_INVALID);
            }
            var sourceItems = items.selectActiveByOrderIdForUpdate(orderId);
            for (var row : sourceItems) {
                if (row.getActualQuantity() == null || row.getActualQuantity().signum() <= 0) {
                    continue;
                }
                var existing = demands.selectActiveBySalesOrderItemId(row.getId());
                if (existing != null) {
                    result.add(existing.getId());
                    continue;
                }
                var demand = new PurchaseDemandEntity();
                demand.setSalesOrderId(orderId);
                demand.setSalesOrderItemId(row.getId());
                demand.setSpuId(row.getSpuId());
                demand.setSkuId(row.getSkuId());
                demand.setSalesOrderNoSnapshot(order.getOrderNo());
                demand.setSpuCodeSnapshot(row.getSpuCodeSnapshot());
                demand.setProductNameSnapshot(row.getProductNameSnapshot());
                demand.setSkuCodeSnapshot(row.getSkuCodeSnapshot());
                demand.setSkuNameSnapshot(row.getProductNameSnapshot() == null
                        ? row.getSkuCodeSnapshot() : row.getProductNameSnapshot());
                demand.setSpecValuesSnapshot(row.getSpecValuesSnapshot());
                demand.setPurchaseUnitSnapshot(row.getSaleUnitSnapshot());
                demand.setProductTypeSnapshot(row.getProductTypeSnapshot());
                demand.setRequiredQuantity(row.getActualQuantity());
                demand.setAllocatedQuantity(BigDecimal.ZERO);
                demand.setFulfilledQuantity(BigDecimal.ZERO);
                demand.setStatus(PurchaseDemandStatus.PENDING);
                demand.setDemandDate(LocalDate.now());
                demand.setVersion(0);
                demand.setDeleted(false);
                demand.setCreatedBy("SYSTEM");
                Long insertedId = demands.insertIfAbsent(demand);
                if (insertedId != null) {
                    result.add(insertedId);
                    continue;
                }
                var concurrent = demands.selectActiveBySalesOrderItemId(row.getId());
                if (concurrent == null) {
                    throw new BusinessException(PurchaseDemandErrorCodes.ALREADY_EXISTS);
                }
                result.add(concurrent.getId());
            }
        }
        return result;
    }

    @Transactional
    public void allocate(PurchaseDemandAllocationRequest request) {
        var demand = demands.selectActiveByIdForUpdate(request.id());
        if (demand == null) {
            throw new BusinessException(PurchaseDemandErrorCodes.NOT_FOUND);
        }
        if (demand.getStatus() == PurchaseDemandStatus.CANCELLED
                || demand.getStatus() == PurchaseDemandStatus.FULFILLED) {
            throw new BusinessException(PurchaseDemandErrorCodes.SOURCE_INVALID);
        }
        if (!Objects.equals(demand.getVersion(), request.version())) {
            throw new BusinessException(PurchaseDemandErrorCodes.VERSION_CONFLICT);
        }
        var orderItem = orderItems.selectActiveById(request.purchaseOrderItemId());
        if (orderItem == null || !Objects.equals(orderItem.getSkuId(), demand.getSkuId())) {
            throw new BusinessException(PurchaseDemandErrorCodes.SOURCE_INVALID);
        }
        var purchaseOrder = purchaseOrders.selectActiveById(orderItem.getPurchaseOrderId());
        if (purchaseOrder == null
                || purchaseOrder.getStatus() != PurchaseOrderStatus.SUBMITTED
                || !Objects.equals(purchaseOrder.getSupplierId(), request.supplierId())
                || !Objects.equals(purchaseOrder.getWarehouseId(), request.warehouseId())) {
            throw new BusinessException(PurchaseDemandErrorCodes.SOURCE_INVALID);
        }

        BigDecimal quantity = new BigDecimal(request.quantity());
        var existing = allocations.selectActiveByOrderItemAndDemandForUpdate(
                orderItem.getId(), demand.getId());
        if (existing != null) {
            if (existing.getAllocatedQuantity().compareTo(quantity) == 0) {
                return;
            }
            throw new BusinessException(PurchaseDemandErrorCodes.ALLOCATION_CONFLICT);
        }
        if (quantity.signum() <= 0
                || demand.getAllocatedQuantity().add(quantity).compareTo(demand.getRequiredQuantity()) > 0) {
            throw new BusinessException(PurchaseDemandErrorCodes.OVER_ALLOCATED);
        }

        suppliers.requireEnabledSupplier(request.supplierId());
        warehouses.requireEnabledWarehouse(request.warehouseId());
        var allocation = new PurchaseDemandAllocationEntity();
        allocation.setPurchaseDemandId(demand.getId());
        allocation.setPurchaseOrderItemId(orderItem.getId());
        allocation.setSalesOrderId(demand.getSalesOrderId());
        allocation.setSalesOrderItemId(demand.getSalesOrderItemId());
        allocation.setSkuId(demand.getSkuId());
        allocation.setAllocatedQuantity(quantity);
        allocation.setDemandSnapshot(JsonNodeFactory.instance.objectNode()
                .put("demandId", demand.getId())
                .put("salesOrderId", demand.getSalesOrderId())
                .put("salesOrderItemId", demand.getSalesOrderItemId())
                .put("skuId", demand.getSkuId())
                .put("requiredQuantity", demand.getRequiredQuantity().toPlainString())
                .put("skuCode", demand.getSkuCodeSnapshot())
                .put("skuName", demand.getSkuNameSnapshot()));
        allocation.setVersion(0);
        allocation.setDeleted(false);
        allocation.setCreatedBy("SYSTEM");
        allocations.insert(allocation);

        demand.setAllocatedQuantity(demand.getAllocatedQuantity().add(quantity));
        demand.setSupplierId(request.supplierId());
        demand.setWarehouseId(request.warehouseId());
        demand.setStatus(demand.getAllocatedQuantity().compareTo(demand.getRequiredQuantity()) == 0
                ? PurchaseDemandStatus.ALLOCATED : PurchaseDemandStatus.PARTIALLY_ALLOCATED);
        demand.setVersion(request.version());
        if (demands.updateById(demand) != 1) {
            throw new BusinessException(PurchaseDemandErrorCodes.VERSION_CONFLICT);
        }
    }

    public List<PurchaseDemandEntity> list() {
        return demands.selectActivePage();
    }

    public PurchaseDemandEntity get(long id) {
        var demand = demands.selectById(id);
        if (demand == null || Boolean.TRUE.equals(demand.getDeleted())) {
            throw new BusinessException(PurchaseDemandErrorCodes.NOT_FOUND);
        }
        return demand;
    }
}
