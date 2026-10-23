package com.xsy.scm.product.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 计量单位字典里记录的量纲。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmUomCategoryEnum {

    /**
     * 重量。
     */
    WEIGHT("重量"),

    /**
     * 数量。
     */
    COUNT("数量"),

    /**
     * 体积。
     */
    VOLUME("体积"),

    /**
     * 长度。
     */
    LENGTH("长度"),

    /**
     * 其他。
     */
    OTHER("其他");

    private final String desc;
}
