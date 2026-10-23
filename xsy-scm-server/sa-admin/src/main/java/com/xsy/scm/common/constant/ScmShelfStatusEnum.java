package com.xsy.scm.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商品上下架状态。
 *
 * <p>
 * {@code desc} 供页面、导入模板下拉与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmShelfStatusEnum {

    /**
     * 上架：可被下单。
     */
    ON_SHELF("上架"),

    /**
     * 下架：不在售，但主档与历史单据保留。
     */
    OFF_SHELF("下架");

    private final String desc;
}
