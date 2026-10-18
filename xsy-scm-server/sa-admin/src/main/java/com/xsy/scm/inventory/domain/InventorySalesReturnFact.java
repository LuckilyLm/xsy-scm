package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 销售退货入库事实。
 *
 * <p>
 * {@code sourceOutboundItemId} 是原销售出库单行：批次仓靠它把退货退回**原批次**，找不到时显式失败而不是猜批次。
 */
public record InventorySalesReturnFact(Long warehouseId, Long skuId, Long returnId, Long returnReceiptItemId,
        BigDecimal quantity, String unit, BigDecimal unitCost, OffsetDateTime occurredAt, String operator,
        Long sourceOutboundItemId) {
}
