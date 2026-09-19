package net.lab1024.sa.admin.module.scm.inventory.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryTransferStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryBalanceDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryTransferDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryTransferEntity;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseReceiptDao;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseReceiptEntity;
import net.lab1024.sa.admin.module.scm.warehouse.constant.WarehouseErrorCode;
import net.lab1024.sa.admin.module.scm.warehouse.support.WarehouseDisableGuard;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * {@link WarehouseDisableGuard} 实现（B1，HD-B1-01；调拨波次新增第四条）。
 *
 * <p>放在 inventory 域是因为停用要同时读 inventory_balance、purchase_order、purchase_receipt
 * 与 inventory_transfer，而 inventory → purchase → warehouse 是本仓库既有的单向依赖方向，
 * 不会反向成环。
 *
 * <p>四条阻塞条件按 HD-B1-01 严格模式逐条短路：
 * <ol>
 *   <li>库存余额 &gt; 0；</li>
 *   <li>在途采购单；</li>
 *   <li>待入库收货单；</li>
 *   <li><b>在途调拨单</b>（调拨波次新增）—— 源仓与目标仓**都算**：源仓的货已经出去了
 *       但账上还没落地到目标仓，目标仓则还欠着一批要入库的货。
 *       任一被停用都会让在途调拨无处可收 / 无据可查。</li>
 * </ol>
 * 软删由 MyBatis-Plus {@code @TableLogic} 在 {@code selectCount} 时自动过滤。
 *
 * <p><b>为什么只在「在途」阻塞、草稿不阻塞</b>：草稿调拨还没有动过任何库存，
 * 停用仓库时它会被自然取消（停用后发出会被 41048 拒绝），不构成风险；
 * 而在途调拨是「货已经离开源仓、还没进目标仓」的真实缺口，必须挡住。
 */
@Component
@RequiredArgsConstructor
public class WarehouseDisableGuardImpl implements WarehouseDisableGuard {

    private final InventoryBalanceDao balanceDao;

    private final PurchaseOrderDao purchaseOrderDao;

    private final PurchaseReceiptDao purchaseReceiptDao;

    private final InventoryTransferDao transferDao;

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
        // 4. 在途调拨单（SHIPPED）→ 禁止停用；源仓与目标仓都要挡。
        if (transferDao.selectCount(new LambdaQueryWrapper<InventoryTransferEntity>()
                .eq(InventoryTransferEntity::getStatus, ScmInventoryTransferStatusEnum.SHIPPED.name())
                .and(wrapper -> wrapper
                        .eq(InventoryTransferEntity::getFromWarehouseId, warehouseId)
                        .or()
                        .eq(InventoryTransferEntity::getToWarehouseId, warehouseId))) > 0) {
            return WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_IN_TRANSIT_TRANSFER;
        }
        return null;
    }
}
