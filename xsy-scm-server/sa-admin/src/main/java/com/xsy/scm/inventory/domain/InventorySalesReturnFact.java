package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record InventorySalesReturnFact(Long warehouseId, Long skuId, Long returnId, Long returnReceiptItemId,
        BigDecimal quantity, String unit, BigDecimal unitCost, OffsetDateTime occurredAt, String operator) {
}
