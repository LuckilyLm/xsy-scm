package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 赠品出库以已确定的赠品权益作为来源，记录仓库、规格、实际数量及操作时间，不虚构销售订单行；同一权益只能出库一次。
 */
public record InventoryPromotionGiftFact(Long warehouseId, Long skuId, Long routeId, Long giftId, BigDecimal quantity,
    OffsetDateTime occurredAt, String operator) {
}
