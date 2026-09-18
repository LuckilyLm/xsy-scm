package net.lab1024.sa.admin.module.scm.inventory.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryBalanceDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseReceiptDao;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseReceiptEntity;
import net.lab1024.sa.admin.module.scm.warehouse.constant.WarehouseErrorCode;
import net.lab1024.sa.admin.module.scm.warehouse.support.WarehouseDisableGuard;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * {@link WarehouseDisableGuard} 实现（B1，HD-B1-01）。
 *
 * <p>放在 inventory 域是因为停用要同时读 inventory_balance、purchase_order、purchase_receipt，
 * 而 inventory → purchase → warehouse 是本仓库既有的单向依赖方向，不会反向成环。
 *
 * <p>三条阻塞条件按 HD-B1-01 严格模式逐条短路：库存余额、在途采购单、待入库收货单。
 * 软删由 MyBatis-Plus {@code @TableLogic} 在 {@code selectCount} 时自动过滤。
 */
@Component
@RequiredArgsConstructor
public class WarehouseDisableGuardImpl implements WarehouseDisableGuard {

    private final InventoryBalanceDao balanceDao;

    private final PurchaseOrderDao purchaseOrderDao;

    private final PurchaseReceiptDao purchaseReceiptDao;

    @Override
    public WarehouseErrorCode disableBlocker(Long warehouseId) {
        // 1. 库存余额 > 0 → 禁止停用。
        if (balanceDao.selectCount(new LambdaQueryWrapper<InventoryBalanceEntity>()
                .eq(InventoryBalanceEntity::getWarehouseId, warehouseId)
                .gt(InventoryBalanceEntity::getQuantity, BigDecimal.ZERO)) > 0) {
            return WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_BALANCE;
        }
        // 2. 在途采购单（SUBMITTED / PARTIALLY_RECEIVED）→ 禁止停用。
        if (purchaseOrderDao.selectCount(new LambdaQueryWrapper<PurchaseOrderEntity>()
                .eq(PurchaseOrderEntity::getWarehouseId, warehouseId)
                .in(PurchaseOrderEntity::getStatus, "SUBMITTED", "PARTIALLY_RECEIVED")) > 0) {
            return WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_INBOUND;
        }
        // 3. 待入库收货单（CONFIRMED 且 putaway_status=PENDING）→ 禁止停用。
        if (purchaseReceiptDao.selectCount(new LambdaQueryWrapper<PurchaseReceiptEntity>()
                .eq(PurchaseReceiptEntity::getWarehouseId, warehouseId)
                .eq(PurchaseReceiptEntity::getStatus, "CONFIRMED")
                .eq(PurchaseReceiptEntity::getPutawayStatus, "PENDING")) > 0) {
            return WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_PENDING_PUTAWAY;
        }
        return null;
    }
}
