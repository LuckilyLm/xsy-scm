package com.xianshuyuan.scm.inventory.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.inventory.entity.InventoryEntity;
import com.xianshuyuan.scm.inventory.entity.InventoryMovementEntity;
import com.xianshuyuan.scm.inventory.entity.InventoryMovementType;
import com.xianshuyuan.scm.inventory.mapper.InventoryMapper;
import com.xianshuyuan.scm.inventory.mapper.InventoryMovementMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryApplicationServiceTest {
    private final InventoryMapper inventories = mock(InventoryMapper.class);
    private final InventoryMovementMapper movements = mock(InventoryMovementMapper.class);
    private final InventoryApplicationService service = new InventoryApplicationService(inventories, movements);

    @Test
    void postPurchaseInIncreasesBalanceAndRecordsExactArithmetic() {
        InventoryEntity balance = balance("7.2500", 3);
        when(inventories.selectByWarehouseAndSkuForUpdate(10L, 20L)).thenReturn(balance);
        when(inventories.increase(30L, 3, new BigDecimal("2.1250"))).thenReturn(1);
        when(movements.nextNumber()).thenReturn("IM-1");

        InventoryMovementEntity result = service.postPurchaseIn(command("2.1250"));

        verify(inventories).insertIfAbsent(10L, 20L, "W10", "Main", "SKU20", "Tomato", "kg");
        verify(inventories).increase(30L, 3, new BigDecimal("2.1250"));
        ArgumentCaptor<InventoryMovementEntity> captor = ArgumentCaptor.forClass(InventoryMovementEntity.class);
        verify(movements).insert(captor.capture());
        assertThat(result).isSameAs(captor.getValue());
        assertThat(result.getMovementNo()).isEqualTo("IM-1");
        assertThat(result.getMovementType()).isEqualTo(InventoryMovementType.PURCHASE_IN);
        assertThat(result.getQuantityBefore()).isEqualByComparingTo("7.2500");
        assertThat(result.getQuantityChange()).isEqualByComparingTo("2.1250");
        assertThat(result.getQuantityAfter()).isEqualByComparingTo("9.3750");
        assertThat(result.getUnitCost()).isEqualByComparingTo("3.5000");
        assertThat(result.getSourceDocumentId()).isEqualTo(40L);
        assertThat(result.getSourceDocumentItemId()).isEqualTo(41L);
        assertThat(result.getConfirmationId()).isEqualTo(42L);
    }

    @Test
    void postPurchaseInReplayReturnsExistingMovementWithoutAnyBalanceWrite() {
        InventoryMovementEntity existing = new InventoryMovementEntity();
        existing.setId(99L);
        when(movements.selectBySource(40L, 41L, 42L)).thenReturn(existing);

        assertThat(service.postPurchaseIn(command("2.1250"))).isSameAs(existing);

        verifyNoInteractions(inventories);
        verify(movements, never()).nextNumber();
        verify(movements, never()).insert(any(InventoryMovementEntity.class));
    }

    @Test
    void postPurchaseInRejectsMissingBalanceAfterInsertAttempt() {
        when(inventories.selectByWarehouseAndSkuForUpdate(10L, 20L)).thenReturn(null);

        assertConflict(() -> service.postPurchaseIn(command("2.1250")));

        verify(inventories, never()).increase(anyLong(), anyInt(), any(BigDecimal.class));
        verify(movements, never()).insert(any(InventoryMovementEntity.class));
    }

    @Test
    void postPurchaseInRejectsOptimisticUpdateConflictWithoutMovement() {
        when(inventories.selectByWarehouseAndSkuForUpdate(10L, 20L)).thenReturn(balance("7.2500", 3));
        when(inventories.increase(30L, 3, new BigDecimal("2.1250"))).thenReturn(0);

        assertConflict(() -> service.postPurchaseIn(command("2.1250")));

        verify(movements, never()).nextNumber();
        verify(movements, never()).insert(any(InventoryMovementEntity.class));
    }

    private void assertConflict(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code()).isEqualTo(40970));
    }

    private InventoryEntity balance(String quantity, int version) {
        InventoryEntity row = new InventoryEntity();
        row.setId(30L);
        row.setQuantity(new BigDecimal(quantity));
        row.setVersion(version);
        return row;
    }

    private PurchaseInCommand command(String quantity) {
        return new PurchaseInCommand(40L, 41L, 42L, 10L, 20L,
                "W10", "Main", "SKU20", "Tomato", "kg",
                new BigDecimal(quantity), new BigDecimal("3.5000"));
    }
}
