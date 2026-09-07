package com.xianshuyuan.scm.purchase.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.order.entity.IdempotencyRecordEntity;
import com.xianshuyuan.scm.order.entity.OrderStatus;
import com.xianshuyuan.scm.order.entity.SalesOrderEntity;
import com.xianshuyuan.scm.order.entity.SalesOrderItemEntity;
import com.xianshuyuan.scm.order.service.IdempotencyService;
import com.xianshuyuan.scm.order.mapper.SalesOrderItemMapper;
import com.xianshuyuan.scm.order.mapper.SalesOrderMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseOrderItemMapper;
import com.xianshuyuan.scm.purchase.dto.PurchaseDemandAllocationRequest;
import com.xianshuyuan.scm.purchase.dto.PurchaseDemandGenerateRequest;
import com.xianshuyuan.scm.purchase.entity.PurchaseDemandAllocationEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseDemandEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseDemandStatus;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderItemEntity;
import com.xianshuyuan.scm.purchase.entity.PurchaseOrderStatus;
import com.xianshuyuan.scm.purchase.mapper.PurchaseDemandAllocationMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseDemandMapper;
import com.xianshuyuan.scm.purchase.mapper.PurchaseOrderMapper;
import com.xianshuyuan.scm.supplier.service.SupplierService;
import com.xianshuyuan.scm.supplier.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PurchaseDemandServiceTest {
    private final PurchaseDemandMapper demands = mock(PurchaseDemandMapper.class);
    private final SalesOrderMapper orders = mock(SalesOrderMapper.class);
    private final SalesOrderItemMapper salesItems = mock(SalesOrderItemMapper.class);
    private final SupplierService suppliers = mock(SupplierService.class);
    private final WarehouseService warehouses = mock(WarehouseService.class);
    private final PurchaseDemandAllocationMapper allocations = mock(PurchaseDemandAllocationMapper.class);
    private final PurchaseOrderItemMapper purchaseItems = mock(PurchaseOrderItemMapper.class);
    private final PurchaseOrderMapper purchaseOrders = mock(PurchaseOrderMapper.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class);
    private final PurchaseDemandService service = new PurchaseDemandService(demands, orders, salesItems,
            suppliers, warehouses, allocations, purchaseItems, purchaseOrders, idempotency);

    @BeforeEach
    void setUp() {
        when(idempotency.claim(any(), any(), any()))
                .thenReturn(new IdempotencyService.Claim(new IdempotencyRecordEntity(), false));
    }

    @Test
    void generationReplaysStoredResultWithoutReadingSourceOrders() {
        var claim = new IdempotencyService.Claim(new IdempotencyRecordEntity(), true);
        when(idempotency.claim(any(), eq("generate-key"), any())).thenReturn(claim);
        when(idempotency.replay(claim, List.class)).thenReturn(List.of(99L));

        assertThat(service.generate(new PurchaseDemandGenerateRequest(List.of(1L)), "generate-key"))
                .containsExactly(99L);

        verifyNoInteractions(orders, salesItems, demands);
    }

    @Test
    void generationReusesExistingDemandAndDoesNotDuplicateIt() {
        when(orders.selectActiveByIdForUpdate(1L)).thenReturn(order(OrderStatus.CONFIRMED));
        when(salesItems.selectActiveByOrderIdForUpdate(1L)).thenReturn(List.of(salesItem()));
        PurchaseDemandEntity existing = demand(); existing.setId(99L);
        when(demands.selectActiveBySalesOrderItemId(11L)).thenReturn(existing);

        assertThat(service.generate(new PurchaseDemandGenerateRequest(List.of(1L)), "generate-key"))
                .containsExactly(99L);
        verify(demands, never()).insertIfAbsent(any(PurchaseDemandEntity.class));
    }

    @Test
    void generationUsesInsertIfAbsentAndReturnsInsertedId() {
        when(orders.selectActiveByIdForUpdate(1L)).thenReturn(order(OrderStatus.CONFIRMED));
        when(salesItems.selectActiveByOrderIdForUpdate(1L)).thenReturn(List.of(salesItem()));
        when(demands.insertIfAbsent(any(PurchaseDemandEntity.class))).thenReturn(77L);

        assertThat(service.generate(new PurchaseDemandGenerateRequest(List.of(1L)), "generate-key"))
                .containsExactly(77L);
    }

    @Test
    void generationReturnsConcurrentCanonicalDemandWhenInsertLosesRace() {
        when(orders.selectActiveByIdForUpdate(1L)).thenReturn(order(OrderStatus.CONFIRMED));
        when(salesItems.selectActiveByOrderIdForUpdate(1L)).thenReturn(List.of(salesItem()));
        PurchaseDemandEntity concurrent = demand();
        concurrent.setId(88L);
        when(demands.selectActiveBySalesOrderItemId(11L)).thenReturn(null, concurrent);
        when(demands.insertIfAbsent(any(PurchaseDemandEntity.class))).thenReturn(null);

        assertThat(service.generate(new PurchaseDemandGenerateRequest(List.of(1L)), "generate-key"))
                .containsExactly(88L);
    }

    @Test
    void generationLocksDistinctSourceOrdersInAscendingOrder() {
        when(orders.selectActiveByIdForUpdate(anyLong())).thenReturn(order(OrderStatus.CONFIRMED));
        when(salesItems.selectActiveByOrderIdForUpdate(anyLong())).thenReturn(List.of());

        service.generate(new PurchaseDemandGenerateRequest(List.of(3L, 1L, 3L, 2L)), "generate-key");

        var inOrder = inOrder(orders);
        inOrder.verify(orders).selectActiveByIdForUpdate(1L);
        inOrder.verify(orders).selectActiveByIdForUpdate(2L);
        inOrder.verify(orders).selectActiveByIdForUpdate(3L);
        verify(orders, times(3)).selectActiveByIdForUpdate(anyLong());
    }

    @Test
    void generationRejectsUnconfirmedSourceOrder() {
        when(orders.selectActiveByIdForUpdate(1L)).thenReturn(order(OrderStatus.PENDING));

        assertThatThrownBy(() -> service.generate(
                new PurchaseDemandGenerateRequest(List.of(1L)), "generate-key"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(PurchaseDemandErrorCodes.SOURCE_INVALID));
        verifyNoInteractions(salesItems, demands);
    }

    @Test
    void allocationPersistsTraceabilitySnapshotAndUpdatesDemandProgress() {
        PurchaseDemandEntity demand = demand();
        PurchaseOrderItemEntity purchaseItem = purchaseItem();
        when(demands.selectActiveByIdForUpdate(10L)).thenReturn(demand);
        when(purchaseItems.selectActiveById(20L)).thenReturn(purchaseItem);
        when(purchaseOrders.selectActiveById(30L)).thenReturn(purchaseOrder());
        when(allocations.selectActiveByOrderItemAndDemandForUpdate(20L, 10L)).thenReturn(null);
        when(demands.updateById(demand)).thenReturn(1);

        service.allocate(new PurchaseDemandAllocationRequest(10L, 20L, "2.0000", 1L, 2L, 4));

        ArgumentCaptor<PurchaseDemandAllocationEntity> captor = ArgumentCaptor.forClass(PurchaseDemandAllocationEntity.class);
        verify(allocations).insert(captor.capture());
        PurchaseDemandAllocationEntity saved = captor.getValue();
        assertThat(saved.getPurchaseDemandId()).isEqualTo(10L);
        assertThat(saved.getSalesOrderItemId()).isEqualTo(11L);
        assertThat(saved.getSkuId()).isEqualTo(3L);
        assertThat(saved.getAllocatedQuantity()).isEqualByComparingTo("2.0000");
        assertThat(saved.getDemandSnapshot().get("requiredQuantity").asText()).isEqualTo("5.0000");
        assertThat(demand.getAllocatedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(demand.getStatus()).isEqualTo(PurchaseDemandStatus.PARTIALLY_ALLOCATED);
        assertThat(demand.getSupplierId()).isEqualTo(1L);
        assertThat(demand.getWarehouseId()).isEqualTo(2L);
    }

    @Test
    void exactAllocationReplayDoesNotInsertOrIncrementDemand() {
        PurchaseDemandEntity demand = demand();
        PurchaseDemandAllocationEntity existing = new PurchaseDemandAllocationEntity();
        existing.setPurchaseOrderItemId(20L);
        existing.setPurchaseDemandId(10L);
        existing.setAllocatedQuantity(new BigDecimal("2.0000"));
        when(demands.selectActiveByIdForUpdate(10L)).thenReturn(demand);
        when(purchaseItems.selectActiveById(20L)).thenReturn(purchaseItem());
        when(purchaseOrders.selectActiveById(30L)).thenReturn(purchaseOrder());
        when(allocations.selectActiveByOrderItemAndDemandForUpdate(20L, 10L)).thenReturn(existing);

        service.allocate(new PurchaseDemandAllocationRequest(10L, 20L, "2.0000", 1L, 2L, 4));

        verify(allocations, never()).insert(any(PurchaseDemandAllocationEntity.class));
        verify(demands, never()).updateById(any(PurchaseDemandEntity.class));
        assertThat(demand.getAllocatedQuantity()).isEqualByComparingTo("1.0000");
    }

    @Test
    void conflictingAllocationReplayReturnsControlledConflict() {
        PurchaseDemandAllocationEntity existing = new PurchaseDemandAllocationEntity();
        existing.setPurchaseOrderItemId(20L);
        existing.setPurchaseDemandId(10L);
        existing.setAllocatedQuantity(new BigDecimal("1.0000"));
        when(demands.selectActiveByIdForUpdate(10L)).thenReturn(demand());
        when(purchaseItems.selectActiveById(20L)).thenReturn(purchaseItem());
        when(purchaseOrders.selectActiveById(30L)).thenReturn(purchaseOrder());
        when(allocations.selectActiveByOrderItemAndDemandForUpdate(20L, 10L)).thenReturn(existing);

        assertThatThrownBy(() -> service.allocate(
                new PurchaseDemandAllocationRequest(10L, 20L, "2.0000", 1L, 2L, 4)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(PurchaseDemandErrorCodes.ALLOCATION_CONFLICT));
        verify(allocations, never()).insert(any(PurchaseDemandAllocationEntity.class));
        verify(demands, never()).updateById(any(PurchaseDemandEntity.class));
    }

    @Test
    void allocationRejectsSkuMismatchBeforeMasterDataOrPersistence() {
        PurchaseDemandEntity demand = demand();
        PurchaseOrderItemEntity purchaseItem = new PurchaseOrderItemEntity();
        purchaseItem.setId(20L);
        purchaseItem.setSkuId(999L);
        when(demands.selectActiveByIdForUpdate(10L)).thenReturn(demand);
        when(purchaseItems.selectActiveById(20L)).thenReturn(purchaseItem);

        assertThatThrownBy(() -> service.allocate(new PurchaseDemandAllocationRequest(10L, 20L, "1.0000", 1L, 2L, 4)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(PurchaseDemandErrorCodes.SOURCE_INVALID));
        verifyNoInteractions(suppliers, warehouses, allocations);
        verify(demands, never()).updateById(any(PurchaseDemandEntity.class));
    }

    @Test
    void allocationRejectsQuantityBeyondUnallocatedBalance() {
        when(demands.selectActiveByIdForUpdate(10L)).thenReturn(demand());
        when(purchaseItems.selectActiveById(20L)).thenReturn(purchaseItem());
        when(purchaseOrders.selectActiveById(30L)).thenReturn(purchaseOrder());

        assertThatThrownBy(() -> service.allocate(new PurchaseDemandAllocationRequest(10L, 20L, "5.0000", 1L, 2L, 4)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(PurchaseDemandErrorCodes.OVER_ALLOCATED));
        verify(allocations, never()).insert(any(PurchaseDemandAllocationEntity.class));
    }

    @Test
    void allocationRejectsStaleVersionBeforeOrderItemLookup() {
        when(demands.selectActiveByIdForUpdate(10L)).thenReturn(demand());

        assertThatThrownBy(() -> service.allocate(new PurchaseDemandAllocationRequest(10L, 20L, "1.0000", 1L, 2L, 3)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(PurchaseDemandErrorCodes.VERSION_CONFLICT));
        verifyNoInteractions(purchaseItems, allocations, suppliers, warehouses);
    }

    private PurchaseOrderItemEntity purchaseItem() {
        PurchaseOrderItemEntity entity = new PurchaseOrderItemEntity();
        entity.setId(20L);
        entity.setPurchaseOrderId(30L);
        entity.setSkuId(3L);
        return entity;
    }

    private PurchaseOrderEntity purchaseOrder() {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(30L);
        entity.setSupplierId(1L);
        entity.setWarehouseId(2L);
        entity.setStatus(PurchaseOrderStatus.SUBMITTED);
        return entity;
    }

    private SalesOrderEntity order(OrderStatus status) { SalesOrderEntity x = new SalesOrderEntity(); x.setId(1L); x.setOrderNo("SO-1"); x.setStatus(status); return x; }
    private SalesOrderItemEntity salesItem() { SalesOrderItemEntity x = new SalesOrderItemEntity(); x.setId(11L); x.setSpuId(6L); x.setSkuId(3L); x.setActualQuantity(new BigDecimal("5.0000")); x.setSkuCodeSnapshot("SKU3"); x.setProductNameSnapshot("Tomato"); return x; }
    private PurchaseDemandEntity demand() { PurchaseDemandEntity x = new PurchaseDemandEntity(); x.setId(10L); x.setSalesOrderId(1L); x.setSalesOrderItemId(11L); x.setSkuId(3L); x.setSkuCodeSnapshot("SKU3"); x.setSkuNameSnapshot("Tomato"); x.setRequiredQuantity(new BigDecimal("5.0000")); x.setAllocatedQuantity(new BigDecimal("1.0000")); x.setStatus(PurchaseDemandStatus.PARTIALLY_ALLOCATED); x.setVersion(4); return x; }
}
