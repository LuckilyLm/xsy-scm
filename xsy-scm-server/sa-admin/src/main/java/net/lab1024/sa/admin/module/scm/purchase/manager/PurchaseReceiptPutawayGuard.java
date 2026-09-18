package net.lab1024.sa.admin.module.scm.purchase.manager;

import net.lab1024.sa.admin.module.scm.purchase.constant.ScmReceiptModeEnum;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmPutawayStatusEnum;

/**
 * 仓库确认入库的前置判定（B1，HD-B1-03）。
 *
 * <p>纯函数，供 {@code PurchaseReceiptService.putaway} 调用，也便于单测锁住状态迁移矩阵：
 * 只有「已确认 + WAREHOUSE_CONFIRM + 待入库」三者同时成立才允许入库。
 */
public final class PurchaseReceiptPutawayGuard {

    private PurchaseReceiptPutawayGuard() {
    }

    public static boolean putawayAllowed(String status, String receiptMode, String putawayStatus) {
        return "CONFIRMED".equals(status)
                && ScmReceiptModeEnum.WAREHOUSE_CONFIRM.name().equals(receiptMode)
                && ScmPutawayStatusEnum.PENDING.name().equals(putawayStatus);
    }
}
