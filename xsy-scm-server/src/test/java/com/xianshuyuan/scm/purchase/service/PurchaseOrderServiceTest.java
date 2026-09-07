package com.xianshuyuan.scm.purchase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.order.entity.IdempotencyRecordEntity;
import com.xianshuyuan.scm.order.service.IdempotencyService;
import com.xianshuyuan.scm.product.entity.ProductSpuEntity;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import com.xianshuyuan.scm.product.entity.ProductType;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import com.xianshuyuan.scm.product.mapper.ProductSpuMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseDemandMapper;
import com.xianshuyuan.scm.purchase.dto.PurchaseOrderCancelRequest;
import com.xianshuyuan.scm.purchase.dto.PurchaseOrderItemRequest;
import com.xianshuyuan.scm.purchase.dto.PurchaseOrderSaveRequest;
import com.xianshuyuan.scm.purchase.entity.*;
import com.xianshuyuan.scm.purchase.mapper.*;
import com.xianshuyuan.scm.supplier.entity.SupplierEntity;
import com.xianshuyuan.scm.supplier.entity.SupplierSkuEntity;
import com.xianshuyuan.scm.supplier.entity.WarehouseEntity;
import com.xianshuyuan.scm.supplier.service.SupplierService;
import com.xianshuyuan.scm.supplier.service.SupplierSkuService;
import com.xianshuyuan.scm.supplier.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PurchaseOrderServiceTest {
    private final PurchaseOrderMapper orders = mock(PurchaseOrderMapper.class);
    private final PurchaseOrderItemMapper items = mock(PurchaseOrderItemMapper.class);
    private final PurchaseOperationLogMapper logs = mock(PurchaseOperationLogMapper.class);
    private final PurchaseDemandAllocationMapper allocations = mock(PurchaseDemandAllocationMapper.class);
    private final SupplierService suppliers = mock(SupplierService.class);
    private final WarehouseService warehouses = mock(WarehouseService.class);
    private final SupplierSkuService supplierSkus = mock(SupplierSkuService.class);
    private final ProductSkuMapper skus = mock(ProductSkuMapper.class);
    private final ProductSpuMapper spus = mock(ProductSpuMapper.class);
    private final PurchaseDemandMapper demands = mock(PurchaseDemandMapper.class);
    private final PurchaseOrderNumberGenerator numbers = mock(PurchaseOrderNumberGenerator.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final PurchaseOrderService service = new PurchaseOrderService(orders, items, logs, allocations,
            suppliers, warehouses, supplierSkus, skus, spus, demands, numbers, idempotency, new ObjectMapper());

    @BeforeEach
    void setUp() {
        when(suppliers.requireEnabledSupplier(1L)).thenReturn(supplier());
        when(warehouses.requireEnabledWarehouse(2L)).thenReturn(warehouse());
        when(supplierSkus.requireEnabledForPurchasing(1L, 3L)).thenReturn(configuration());
        when(skus.selectOrderableByIds(List.of(3L))).thenReturn(List.of(sku()));
        when(spus.selectById(6L)).thenReturn(spu());
        when(numbers.next()).thenReturn("PO-1");
        when(items.insert(any(PurchaseOrderItemEntity.class))).thenAnswer(invocation -> {
            PurchaseOrderItemEntity row = invocation.getArgument(0);
            if (row.getId() == null) {
                row.setId(100L);
            }
            return 1;
        });
        when(demands.selectActiveByIdForUpdate(anyLong())).thenAnswer(invocation -> {
            var d = new PurchaseDemandEntity();
            d.setId(invocation.getArgument(0)); d.setSkuId(invocation.getArgument(0).equals(31L) ? 5L : 3L);
            d.setSalesOrderId(40L); d.setSalesOrderItemId(41L); d.setRequiredQuantity(new BigDecimal("10.0000"));
            d.setAllocatedQuantity(BigDecimal.ZERO); d.setStatus(PurchaseDemandStatus.PENDING); d.setVersion(0);
            return d;
        });
        when(allocations.selectActiveByDemandId(anyLong())).thenReturn(List.of());
        when(allocations.insert(any(PurchaseDemandAllocationEntity.class))).thenReturn(1);
        when(demands.updateById(any(PurchaseDemandEntity.class))).thenReturn(1);
    }

    @Test
    void createReplaysStoredResultWithoutWritingPurchaseData() {
        IdempotencyService.Claim claim = new IdempotencyService.Claim(new IdempotencyRecordEntity(), true);
        when(idempotency.claim(eq("PURCHASE_ORDER_CREATE"), eq("same-key"), any())).thenReturn(claim);
        when(idempotency.replay(claim, Long.class)).thenReturn(88L);

        assertThat(service.create(request(null, item(null, null)), "same-key")).isEqualTo(88L);

        verifyNoInteractions(orders, items, logs, suppliers, warehouses, supplierSkus, skus, numbers);
    }

    @Test
    void createPersistsCalculatedItemCompletesIdempotencyAndWritesOperationLog() {
        IdempotencyService.Claim claim = new IdempotencyService.Claim(new IdempotencyRecordEntity(), false);
        when(idempotency.claim(eq("PURCHASE_ORDER_CREATE"), eq("new-key"), any())).thenReturn(claim);
        when(orders.insert(any(PurchaseOrderEntity.class))).thenAnswer(invocation -> { invocation.<PurchaseOrderEntity>getArgument(0).setId(10L); return 1; });

        assertThat(service.create(request(null, item(null, null)), "new-key")).isEqualTo(10L);

        ArgumentCaptor<PurchaseOrderItemEntity> itemCaptor = ArgumentCaptor.forClass(PurchaseOrderItemEntity.class);
        verify(items).insert(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getSpuCodeSnapshot()).isEqualTo("SPU6");
        assertThat(itemCaptor.getValue().getPlannedQuantity()).isEqualByComparingTo("2.0000");
        assertThat(itemCaptor.getValue().getLineAmount()).isEqualByComparingTo("7.0000");
        ArgumentCaptor<PurchaseOperationLogEntity> logCaptor = ArgumentCaptor.forClass(PurchaseOperationLogEntity.class);
        verify(logs).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getOperationType()).isEqualTo("CREATE");
        assertThat(logCaptor.getValue().getOperator()).isEqualTo("SYSTEM");
        verify(idempotency).complete(claim, "PURCHASE_ORDER", 10L, 10L);
        verify(allocations).insert(argThat((PurchaseDemandAllocationEntity row) -> row.getPurchaseDemandId().equals(30L)
                && row.getAllocatedQuantity().compareTo(new BigDecimal("2.0000")) == 0));
        verify(demands).updateById(argThat((PurchaseDemandEntity row) -> row.getId().equals(30L)
                && row.getAllocatedQuantity().compareTo(new BigDecimal("2.0000")) == 0));
    }

    @Test
    void updateRetainsExistingIdentityCreatesNewAndSoftDeletesRemoved() {
        PurchaseOrderEntity order = order(PurchaseOrderStatus.DRAFT, 4);
        PurchaseOrderItemEntity retained = existingItem(20L, 3L, 5);
        PurchaseOrderItemEntity removed = existingItem(21L, 4L, 2);
        when(orders.selectActiveById(10L)).thenReturn(order);
        when(orders.updateById(order)).thenReturn(1);
        when(items.selectActiveByOrderIdForUpdate(10L)).thenReturn(List.of(retained, removed));
        when(items.updateById(retained)).thenReturn(1);
        when(items.softDeleteOwnedWithVersion(10L, 21L, 2)).thenReturn(1);
        when(supplierSkus.requireEnabledForPurchasing(1L, 5L)).thenReturn(configuration());
        ProductSkuEntity newSku = sku(); newSku.setId(5L); newSku.setSkuCode("SKU5");
        when(skus.selectOrderableByIds(List.of(5L))).thenReturn(List.of(newSku));

        service.update(10L, request(4, new PurchaseOrderItemRequest(20L, 3L, "3.0000", "4.0000", 30L, 5),
                new PurchaseOrderItemRequest(null, 5L, "1.0000", "2.0000", 31L, null)));

        assertThat(retained.getId()).isEqualTo(20L);
        assertThat(retained.getPlannedQuantity()).isEqualByComparingTo("3.0000");
        verify(items).updateById(retained);
        verify(items).softDeleteOwnedWithVersion(10L, 21L, 2);
        verify(items).insert(argThat((PurchaseOrderItemEntity row) -> row.getSkuId().equals(5L)));
    }

    @Test
    void updateAdjustsRetainedItemAllocationWithoutCreatingDuplicate() {
        PurchaseOrderEntity order = order(PurchaseOrderStatus.DRAFT, 4);
        order.setSupplierId(1L);
        order.setWarehouseId(2L);
        PurchaseOrderItemEntity retained = existingItem(20L, 3L, 5);
        PurchaseDemandAllocationEntity allocation = new PurchaseDemandAllocationEntity();
        allocation.setId(50L);
        allocation.setPurchaseDemandId(30L);
        allocation.setPurchaseOrderItemId(20L);
        allocation.setAllocatedQuantity(new BigDecimal("2.0000"));
        allocation.setVersion(0);
        PurchaseDemandEntity demand = new PurchaseDemandEntity();
        demand.setId(30L);
        demand.setSkuId(3L);
        demand.setRequiredQuantity(new BigDecimal("10.0000"));
        demand.setAllocatedQuantity(new BigDecimal("2.0000"));
        demand.setStatus(PurchaseDemandStatus.PARTIALLY_ALLOCATED);
        demand.setVersion(3);
        when(orders.selectActiveById(10L)).thenReturn(order);
        when(orders.updateById(order)).thenReturn(1);
        when(items.selectActiveByOrderIdForUpdate(10L)).thenReturn(List.of(retained));
        when(items.updateById(retained)).thenReturn(1);
        when(allocations.selectActiveByOrderItemIdForUpdate(20L)).thenReturn(allocation);
        when(demands.selectActiveByIdForUpdate(30L)).thenReturn(demand);
        when(allocations.updateById(allocation)).thenReturn(1);
        when(demands.updateById(demand)).thenReturn(1);

        service.update(10L, request(4,
                new PurchaseOrderItemRequest(20L, 3L, "3.0000", "4.0000", 30L, 5)));

        assertThat(allocation.getAllocatedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(demand.getAllocatedQuantity()).isEqualByComparingTo("3.0000");
        verify(allocations).updateById(allocation);
        verify(allocations, never()).insert(any(PurchaseDemandAllocationEntity.class));
        verify(demands).updateById(demand);
    }

    @Test
    void updateDeallocatesDemandWhenAllocatedItemIsRemoved() {
        PurchaseOrderEntity order = order(PurchaseOrderStatus.DRAFT, 4);
        order.setSupplierId(1L);
        order.setWarehouseId(2L);
        PurchaseOrderItemEntity removed = existingItem(21L, 4L, 2);
        PurchaseDemandAllocationEntity allocation = new PurchaseDemandAllocationEntity();
        allocation.setId(50L);
        allocation.setPurchaseDemandId(30L);
        allocation.setPurchaseOrderItemId(21L);
        allocation.setAllocatedQuantity(new BigDecimal("2.0000"));
        allocation.setVersion(1);
        PurchaseDemandEntity demand = new PurchaseDemandEntity();
        demand.setId(30L);
        demand.setSkuId(4L);
        demand.setRequiredQuantity(new BigDecimal("10.0000"));
        demand.setAllocatedQuantity(new BigDecimal("2.0000"));
        demand.setStatus(PurchaseDemandStatus.PARTIALLY_ALLOCATED);
        demand.setVersion(3);
        when(orders.selectActiveById(10L)).thenReturn(order);
        when(orders.updateById(order)).thenReturn(1);
        when(items.selectActiveByOrderIdForUpdate(10L)).thenReturn(List.of(removed));
        when(items.softDeleteOwnedWithVersion(10L, 21L, 2)).thenReturn(1);
        when(allocations.selectActiveByOrderItemIdForUpdate(21L)).thenReturn(allocation);
        when(allocations.softDeleteWithVersion(50L, 1)).thenReturn(1);
        when(demands.selectActiveByIdForUpdate(30L)).thenReturn(demand);
        when(demands.updateById(demand)).thenReturn(1);

        service.update(10L, request(4));

        assertThat(allocation.getDeleted()).isTrue();
        assertThat(demand.getAllocatedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(demand.getStatus()).isEqualTo(PurchaseDemandStatus.PENDING);
        verify(allocations).softDeleteWithVersion(50L, 1);
        verify(demands).updateById(demand);
    }

    @Test
    void updateRejectsStaleHeaderVersionBeforeReconcilingItems() {
        when(orders.selectActiveById(10L)).thenReturn(order(PurchaseOrderStatus.DRAFT, 4));

        assertThatThrownBy(() -> service.update(10L, request(3, item(null, null))))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(PurchaseOrderErrorCodes.VERSION_CONFLICT));
        verifyNoInteractions(items);
    }

    @Test
    void submitRejectsNonDraftStateAndDoesNotWriteLog() {
        IdempotencyService.Claim claim = new IdempotencyService.Claim(new IdempotencyRecordEntity(), false);
        when(idempotency.claim(anyString(), eq("submit-key"), any())).thenReturn(claim);
        when(orders.selectActiveById(10L)).thenReturn(order(PurchaseOrderStatus.SUBMITTED, 1));

        assertThatThrownBy(() -> service.submit(10L, 1, "submit-key"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(PurchaseOrderErrorCodes.INVALID_STATE));
        verify(logs, never()).insert(any(PurchaseOperationLogEntity.class));
        verify(idempotency, never()).complete(any(), anyString(), anyLong(), any());
    }

    @Test
    void cancelTransitionsOrderAndLogsReason() {
        IdempotencyService.Claim claim = new IdempotencyService.Claim(new IdempotencyRecordEntity(), false);
        PurchaseOrderEntity order = order(PurchaseOrderStatus.SUBMITTED, 1);
        when(idempotency.claim(anyString(), eq("cancel-key"), any())).thenReturn(claim);
        when(orders.selectActiveById(10L)).thenReturn(order);
        when(orders.updateById(order)).thenReturn(1);

        service.cancel(10L, new PurchaseOrderCancelRequest(1, "supplier unavailable"), "cancel-key");

        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
        ArgumentCaptor<PurchaseOperationLogEntity> captor = ArgumentCaptor.forClass(PurchaseOperationLogEntity.class);
        verify(logs).insert(captor.capture());
        assertThat(captor.getValue().getOperationType()).isEqualTo("CANCEL");
        assertThat(captor.getValue().getReason()).isEqualTo("supplier unavailable");
    }

    @Test
    void detailReturnsTypedNestedResponses() {
        PurchaseOrderEntity order = order(PurchaseOrderStatus.DRAFT, 2);
        PurchaseOrderItemEntity item = existingItem(20L, 3L, 5);
        PurchaseDemandAllocationEntity allocation = new PurchaseDemandAllocationEntity();
        allocation.setId(30L); allocation.setPurchaseDemandId(31L); allocation.setPurchaseOrderItemId(20L);
        allocation.setSalesOrderId(40L); allocation.setSalesOrderItemId(41L); allocation.setSkuId(3L);
        allocation.setAllocatedQuantity(new BigDecimal("2.0000")); allocation.setVersion(0);
        PurchaseOperationLogEntity log = new PurchaseOperationLogEntity();
        log.setId(50L); log.setOperationType("CREATE"); log.setAfterData(JsonNodeFactory.instance.objectNode().put("status", "DRAFT"));
        when(orders.selectActiveById(10L)).thenReturn(order);
        when(items.selectActiveByOrderId(10L)).thenReturn(List.of(item));
        when(allocations.selectActiveByOrderId(10L)).thenReturn(List.of(allocation));
        when(logs.selectByOrderId(10L)).thenReturn(List.of(log));

        var response = service.detail(10L);

        assertThat(response.items()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(20L);
            assertThat(row.version()).isEqualTo(5);
            assertThat(row.skuCode()).isEqualTo("SKU3");
        });
        assertThat(response.allocations()).singleElement().extracting("purchaseDemandId").isEqualTo(31L);
        assertThat(response.operationLogs()).singleElement().extracting("operationType").isEqualTo("CREATE");
    }

    private PurchaseOrderSaveRequest request(Integer version, PurchaseOrderItemRequest... rows) {
        return new PurchaseOrderSaveRequest(1L, 2L, 7L, null, "remark", version, List.of(rows));
    }

    private PurchaseOrderItemRequest item(Long id, Integer version) {
        return new PurchaseOrderItemRequest(id, 3L, "2.0000", "3.5000", 30L, version);
    }

    private SupplierEntity supplier() { SupplierEntity x = new SupplierEntity(); x.setId(1L); x.setSupplierCode("S1"); x.setName("Supplier"); return x; }
    private WarehouseEntity warehouse() { WarehouseEntity x = new WarehouseEntity(); x.setId(2L); x.setWarehouseCode("W1"); x.setName("Warehouse"); return x; }
    private SupplierSkuEntity configuration() { SupplierSkuEntity x = new SupplierSkuEntity(); x.setPurchaseUnit("box"); return x; }
    private ProductSpuEntity spu() { ProductSpuEntity x = new ProductSpuEntity(); x.setId(6L); x.setSpuCode("SPU6"); return x; }
    private ProductSkuEntity sku() { ProductSkuEntity x = new ProductSkuEntity(); x.setId(3L); x.setSpuId(6L); x.setSkuCode("SKU3"); x.setProductName("Tomato"); x.setSpecValues(Map.of("size", "large")); x.setProductType(ProductType.STANDARD); return x; }
    private PurchaseOrderEntity order(PurchaseOrderStatus status, int version) { PurchaseOrderEntity x = new PurchaseOrderEntity(); x.setId(10L); x.setOrderNo("PO-1"); x.setStatus(status); x.setVersion(version); x.setDeleted(false); return x; }
    private PurchaseOrderItemEntity existingItem(long id, long skuId, int version) { PurchaseOrderItemEntity x = new PurchaseOrderItemEntity(); x.setId(id); x.setPurchaseOrderId(10L); x.setSpuId(6L); x.setSkuId(skuId); x.setSkuCodeSnapshot("SKU" + skuId); x.setPlannedQuantity(new BigDecimal("2.0000")); x.setReceivedQuantity(BigDecimal.ZERO); x.setPurchasePrice(new BigDecimal("3.5000")); x.setLineAmount(new BigDecimal("7.0000")); x.setVersion(version); return x; }
}
