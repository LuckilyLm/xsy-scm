package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 促销赠品出库事实。
 *
 * <p>
 * 与 {@link InventoryOutboundFact} 的差别：赠品**不挂订单行**，因此源身份是冻结的赠品权益
 * （{@code order_promotion_gift.id}）而不是出库单行；出库单头只作发车事实的溯源，不参与防重。
 *
 * <p>
 * 防重锚点是 {@code (source_document_type = ORDER_PROMOTION_GIFT, source_document_item_id = giftId)} ——
 * 一行权益至多出一条赠品出库流水，重复发车不会重复扣赠品库存。
 *
 * <p>
 * {@code occurredAt} 取**发车时刻**，{@code operator} 取发车操作人。
 */
public record InventoryPromotionGiftFact(Long warehouseId, Long skuId, Long routeId, Long giftId, BigDecimal quantity,
        OffsetDateTime occurredAt, String operator) {
}
