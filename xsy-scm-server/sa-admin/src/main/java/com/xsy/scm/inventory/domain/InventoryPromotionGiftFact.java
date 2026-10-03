package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 促销赠品出库事实（库存域自己的入参记录）。
 *
 * <p>
 * 与 {@link InventoryOutboundFact} 的差别：赠品**不挂订单行**，因此源身份是冻结的赠品权益
 * （{@code order_promotion_gift.id}）而不是出库单行；出库单头在这里只作发车事实的溯源
 * （{@code routeId}），不参与防重。
 *
 * <p>
 * 防重锚点是 {@code (source_document_type = ORDER_PROMOTION_GIFT, source_document_item_id = giftId)}
 * —— 一行权益至多出一条赠品出库流水，重复发车不会重复扣赠品库存。
 *
 * @param warehouseId
 *            发货仓，与本次销售出库同一个仓
 * @param skuId
 *            赠品 SKU
 * @param routeId
 *            发车业务事实（配送线路 id），只做头级溯源
 * @param giftId
 *            {@code order_promotion_gift.id}，防重锚点
 * @param quantity
 *            出库数量，必须为正（方向由 movementType 表达，数量恒正）
 * @param occurredAt
 *            发生时刻 —— 取**发车时刻**，不是写入时刻
 * @param operator
 *            发车操作人
 */
public record InventoryPromotionGiftFact(Long warehouseId, Long skuId, Long routeId, Long giftId,
        BigDecimal quantity, OffsetDateTime occurredAt, String operator) {
}
