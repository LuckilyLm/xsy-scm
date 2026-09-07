package com.xianshuyuan.scm.inventory.service;

import java.math.BigDecimal;

public record PurchaseInCommand(
        long receiptId, long receiptItemId, long confirmationId,
        long warehouseId, long skuId,
        String warehouseCode, String warehouseName,
        String skuCode, String skuName, String unit,
        BigDecimal quantity, BigDecimal unitCost
) {
}
