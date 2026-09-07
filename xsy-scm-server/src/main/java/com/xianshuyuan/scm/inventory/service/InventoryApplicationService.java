package com.xianshuyuan.scm.inventory.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.common.exception.ErrorCode;
import com.xianshuyuan.scm.inventory.entity.*;
import com.xianshuyuan.scm.inventory.mapper.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class InventoryApplicationService {
    private static final ErrorCode CONFLICT = new ErrorCode(40970, HttpStatus.CONFLICT, "库存余额并发冲突");
    private final InventoryMapper inventories;
    private final InventoryMovementMapper movements;

    public InventoryApplicationService(InventoryMapper inventories, InventoryMovementMapper movements) {
        this.inventories = inventories;
        this.movements = movements;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryMovementEntity postPurchaseIn(PurchaseInCommand command) {
        var existing = movements.selectBySource(command.receiptId(), command.receiptItemId(), command.confirmationId());
        if (existing != null) return existing;
        inventories.insertIfAbsent(command.warehouseId(), command.skuId(), command.warehouseCode(), command.warehouseName(), command.skuCode(), command.skuName(), command.unit());
        var balance = inventories.selectByWarehouseAndSkuForUpdate(command.warehouseId(), command.skuId());
        if (balance == null) throw new BusinessException(CONFLICT);
        var before = balance.getQuantity();
        var after = before.add(command.quantity());
        if (inventories.increase(balance.getId(), balance.getVersion(), command.quantity()) != 1)
            throw new BusinessException(CONFLICT);
        var movement = new InventoryMovementEntity();
        movement.setMovementNo(movements.nextNumber());
        movement.setOccurredAt(OffsetDateTime.now());
        movement.setWarehouseId(command.warehouseId());
        movement.setSkuId(command.skuId());
        movement.setWarehouseCodeSnapshot(command.warehouseCode());
        movement.setWarehouseNameSnapshot(command.warehouseName());
        movement.setSkuCodeSnapshot(command.skuCode());
        movement.setSkuNameSnapshot(command.skuName());
        movement.setMovementType(InventoryMovementType.PURCHASE_IN);
        movement.setSourceDocumentType("PURCHASE_RECEIPT");
        movement.setSourceDocumentId(command.receiptId());
        movement.setSourceDocumentItemId(command.receiptItemId());
        movement.setConfirmationId(command.confirmationId());
        movement.setQuantityBefore(before);
        movement.setQuantityChange(command.quantity());
        movement.setQuantityAfter(after);
        movement.setUnit(command.unit());
        movement.setUnitCost(command.unitCost());
        movement.setOperator("SYSTEM");
        movement.setVersion(0);
        movement.setDeleted(false);
        movement.setCreatedBy("SYSTEM");
        movements.insert(movement);
        return movement;
    }
}
