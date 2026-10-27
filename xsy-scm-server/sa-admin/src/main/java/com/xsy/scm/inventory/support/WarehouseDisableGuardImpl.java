package com.xsy.scm.inventory.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.inventory.constant.ScmInventoryTransferStatusEnum;
import com.xsy.scm.inventory.dao.InventoryBalanceDao;
import com.xsy.scm.inventory.dao.InventoryTransferDao;
import com.xsy.scm.inventory.domain.entity.InventoryBalanceEntity;
import com.xsy.scm.inventory.domain.entity.InventoryTransferEntity;
import com.xsy.scm.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.purchase.dao.PurchaseReceiptDao;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.purchase.constant.ScmReceiptStatusEnum;
import com.xsy.scm.purchase.constant.ScmPutawayStatusEnum;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseReceiptEntity;
import com.xsy.scm.warehouse.constant.WarehouseErrorCode;
import com.xsy.scm.warehouse.support.WarehouseDisableGuard;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 仓库停用前检查四项：库存是否有余量、是否有未完成采购单、待入库收货单、在途调拨。调拨的调出仓和调入仓都要检查；任一条件不满足就拒绝停用。
 */
@Component
@RequiredArgsConstructor
public class WarehouseDisableGuardImpl implements WarehouseDisableGuard {

    private final InventoryBalanceDao inventoryBalanceDao;

    private final PurchaseOrderDao purchaseOrderDao;

    private final PurchaseReceiptDao purchaseReceiptDao;

    private final InventoryTransferDao inventoryTransferDao;

    @Override
    public WarehouseErrorCode disableBlocker(Long warehouseId) {
        // 1. 库存余额 > 0 → 禁止停用。
        if (inventoryBalanceDao.selectCount(
            new LambdaQueryWrapper<InventoryBalanceEntity>().eq(InventoryBalanceEntity::getWarehouseId, warehouseId)
                .gt(InventoryBalanceEntity::getQuantity, BigDecimal.ZERO)) > 0) {
            return WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_BALANCE;
        }
        // 2. 在途采购单（SUBMITTED / PARTIALLY_RECEIVED）→ 禁止停用。
        if (purchaseOrderDao.selectCount(new LambdaQueryWrapper<PurchaseOrderEntity>()
            .eq(PurchaseOrderEntity::getWarehouseId, warehouseId).in(PurchaseOrderEntity::getStatus,
                ScmPurchaseStatusEnum.SUBMITTED.name(), ScmPurchaseStatusEnum.PARTIALLY_RECEIVED.name())) > 0) {
            return WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_INBOUND;
        }
        // 3. 待入库收货单（CONFIRMED 且 putaway_status=PENDING）→ 禁止停用。
        if (purchaseReceiptDao.selectCount(
            new LambdaQueryWrapper<PurchaseReceiptEntity>().eq(PurchaseReceiptEntity::getWarehouseId, warehouseId)
                .eq(PurchaseReceiptEntity::getStatus, ScmReceiptStatusEnum.CONFIRMED.name())
                .eq(PurchaseReceiptEntity::getPutawayStatus, ScmPutawayStatusEnum.PENDING.name())) > 0) {
            return WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_PENDING_PUTAWAY;
        }
        // 4. 在途调拨单（SHIPPED）→ 禁止停用；源仓与目标仓都要挡。
        if (inventoryTransferDao.selectCount(new LambdaQueryWrapper<InventoryTransferEntity>()
            .eq(InventoryTransferEntity::getStatus, ScmInventoryTransferStatusEnum.SHIPPED.name())
            .and(wrapper -> wrapper.eq(InventoryTransferEntity::getFromWarehouseId, warehouseId).or()
                .eq(InventoryTransferEntity::getToWarehouseId, warehouseId))) > 0) {
            return WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_IN_TRANSIT_TRANSFER;
        }
        return null;
    }

    @Override
    public boolean hasNegativeStock(Long warehouseId) {
        return inventoryBalanceDao.selectCount(
            new LambdaQueryWrapper<InventoryBalanceEntity>().eq(InventoryBalanceEntity::getWarehouseId, warehouseId)
                .lt(InventoryBalanceEntity::getQuantity, BigDecimal.ZERO)) > 0;
    }
}
