package com.xsy.scm.promotion.support;

import java.math.BigDecimal;

/**
 * 赠品出库要用的**权益事实**：营销域交给配送/库存域的最小字段集。
 *
 * <p>
 * 只给「哪一行权益、哪个 SKU、多少」，不给活动名、赠品名与规则 —— 出库只关心发什么货，
 * 解释性的字段留给订单详情读，避免让库存域为了打日志去理解营销规则。
 *
 * @param giftId
 *            {@code order_promotion_gift.id}，同时是库存流水的防重锚点
 * @param salesOrderId
 *            来源订单
 * @param skuId
 *            赠品 SKU
 * @param quantity
 *            赠品数量，恒 &gt; 0
 */
public record PromotionGiftFact(Long giftId, Long salesOrderId, Long skuId, BigDecimal quantity) {
}
