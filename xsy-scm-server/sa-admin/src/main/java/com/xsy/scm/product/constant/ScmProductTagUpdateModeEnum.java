package com.xsy.scm.product.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商品标签的批量写入方式。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmProductTagUpdateModeEnum {

    /**
     * 以请求内容整体替换既有标签。
     */
    REPLACE("整体替换"),

    /**
     * 在既有标签上追加。
     */
    ADD("追加"),

    /**
     * 从既有标签中移除。
     */
    REMOVE("移除");

    private final String desc;
}
