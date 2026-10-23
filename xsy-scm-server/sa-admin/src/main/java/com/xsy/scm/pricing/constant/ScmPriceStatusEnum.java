package com.xsy.scm.pricing.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商品对当前客户是否已有可用价格。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPriceStatusEnum {

    /** 已定价：可解析出有效价格。 */
    PRICED("已定价"),

    /** 未定价：没有任何价格来源可命中。 */
    UNPRICED("未定价");

    private final String desc;
}
