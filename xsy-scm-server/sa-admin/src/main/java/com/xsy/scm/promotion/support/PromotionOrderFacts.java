package com.xsy.scm.promotion.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单优惠计算只使用服务端确认的订单与商品信息，不信任前端传入的价格和活动结果。
 */
public record PromotionOrderFacts(Long salesOrderId, Long customerId, List<Line> lines) {

    /**
     * 订单行。
     *
     * @param orderItemId
     *            订单行主键
     * @param skuId
     *            行上的 SKU；限时特价按 SKU 命中，因此必须有它
     * @param quantity
     *            行数量（下单量）；限时特价的让利 = 行基础金额 − 数量 × 特价
     * @param baseAmount
     *            优惠分摊的行基础金额（{@code ordered_line_amount}）
     */
    public record Line(Long orderItemId, Long skuId, BigDecimal quantity, BigDecimal baseAmount) {
    }
}
