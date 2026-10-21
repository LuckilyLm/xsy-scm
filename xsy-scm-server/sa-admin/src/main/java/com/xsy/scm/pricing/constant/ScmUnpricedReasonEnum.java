package com.xsy.scm.pricing.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商品无法定价的原因，用于定价排查。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmUnpricedReasonEnum {

    /** 没有任何可命中的价格来源。 */
    NO_PRICE_SOURCE("无可用价格来源"),

    /** 协议价计算缺少成本价。 */
    AGREEMENT_COST_MISSING("协议价缺少成本"),

    /** 协议价计算缺少市场价。 */
    AGREEMENT_MARKET_PRICE_MISSING("协议价缺少市场价"),

    /** 协议价计算结果无效。 */
    AGREEMENT_RESULT_INVALID("协议价计算结果无效");

    private final String desc;
}
