package com.xsy.scm.product.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商品主档的储存方式。
 *
 * <p>
 * {@code desc} 供页面、导入模板下拉与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmStorageMethodEnum {

    /**
     * 常温储存。
     */
    AMBIENT("常温"),

    /**
     * 冷藏储存。
     */
    CHILLED("冷藏"),

    /**
     * 冷冻储存。
     */
    FROZEN("冷冻");

    private final String desc;
}
