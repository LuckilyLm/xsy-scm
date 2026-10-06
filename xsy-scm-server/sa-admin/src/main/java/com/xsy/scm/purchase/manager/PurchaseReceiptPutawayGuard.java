package com.xsy.scm.purchase.manager;

import com.xsy.scm.purchase.constant.ScmReceiptModeEnum;
import com.xsy.scm.purchase.constant.ScmPutawayStatusEnum;
import com.xsy.scm.purchase.constant.ScmReceiptStatusEnum;

/**
 * 仓库确认入库的前置判定。
 *
 * <p>
 * 纯函数，由 {@code PurchaseReceiptService.putaway} 调用，集中校验入库状态前置条件：只有「已确认 + WAREHOUSE_CONFIRM + 待入库」三者同时成立才允许入库。
 */
public final class PurchaseReceiptPutawayGuard {

    private PurchaseReceiptPutawayGuard() {
    }

    public static boolean putawayAllowed(String receiptStatus, String receiptMode, String putawayStatus) {
        return ScmReceiptStatusEnum.CONFIRMED.name().equals(receiptStatus)
                && ScmReceiptModeEnum.WAREHOUSE_CONFIRM.name().equals(receiptMode)
                && ScmPutawayStatusEnum.PENDING.name().equals(putawayStatus);
    }
}
