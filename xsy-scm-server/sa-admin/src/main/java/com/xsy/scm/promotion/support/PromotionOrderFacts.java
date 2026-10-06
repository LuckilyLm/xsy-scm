package com.xsy.scm.promotion.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单域交给营销域的<b>只读事实</b>：优惠冻结只按这些字段计算，不再接受客户端传入的客户与行金额。
 *
 * <p>
 * 为什么由订单域装配而不是营销域回读订单表：冻结发生在订单确认事务里，此刻订单与行已经在 同一把行锁下被读过并写完实重行金额；再回读一次会多一条跨域依赖，也可能读到不同时点的行。
 * 因此这里是一个<b>单向传入的契约</b>：订单域负责「订单是什么」，营销域负责「按规则减多少」。
 *
 * <p>
 * {@code baseAmount} 取订单行的 {@code ordered_line_amount}（下单量 × 锁定单价，提交时写入）。 该口径由负责人在 2026-10-03 确认；不要改用
 * {@code settlement_line_amount}（实重 × 锁定单价）， 两者在非标品实重与下单量不一致时不同，会让同一单的优惠金额随称重结果变化。
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
