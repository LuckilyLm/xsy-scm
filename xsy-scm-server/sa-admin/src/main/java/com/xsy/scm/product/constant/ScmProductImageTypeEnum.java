package com.xsy.scm.product.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商品图片的内容角色；是否主图仍由 {@code is_primary} 表达。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmProductImageTypeEnum {

    /** 商品图册，用于列表与详情轮播。 */
    GALLERY("图册"),

    /** 详情长图，用于商品说明。 */
    DETAIL("详情图");

    private final String desc;
}
