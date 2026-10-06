package com.xsy.scm.promotion.support;

import java.math.BigDecimal;

/**
 * 赠品出库要用的权益事实：营销域交给配送/库存域的最小字段集。
 *
 * <p>
 * 只给哪一行权益、哪个 SKU、多少，不给活动名、赠品名与规则。出库只关心发什么货， 解释性字段留给订单详情读，避免让库存域为了打日志去理解营销规则。
 */
public record PromotionGiftFact(Long giftId, Long salesOrderId, Long skuId, BigDecimal quantity) {
}
