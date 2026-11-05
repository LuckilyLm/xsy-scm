package com.xsy.scm.product.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 计量单位字典里记录的量纲。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存时仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmUomCategoryEnum {

    WEIGHT("重量"),

    COUNT("数量"),

    VOLUME("体积"),

    LENGTH("长度"),

    OTHER("其他");

    private final String desc;
}
