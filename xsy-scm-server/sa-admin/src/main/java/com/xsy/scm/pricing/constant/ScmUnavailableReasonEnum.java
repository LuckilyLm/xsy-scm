package com.xsy.scm.pricing.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商品对客户不可用的原因，用于定价排查。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmUnavailableReasonEnum {

    /** 商品规格不存在。 */
    SKU_NOT_FOUND("规格不存在"),

    /** 商品规格已下架。 */
    SKU_OFF_SHELF("规格已下架"),

    /** 商品主档已下架。 */
    SPU_OFF_SHELF("商品已下架"),

    /** 所属分类已停用。 */
    CATEGORY_DISABLED("分类已停用"),

    /** 客户无权查看该商品。 */
    NOT_VISIBLE("客户不可见");

    private final String desc;
}
