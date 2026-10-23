package com.xsy.scm.product.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商品主档的可用状态，与「是否上架」相互独立。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存时仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmProductMasterStatusEnum {

    /** 启用：可作为在售商品维护。 */
    ENABLED("启用"),

    /** 停用：暂停维护，历史单据保留。 */
    DISABLED("停用"),

    /** 归档：只读留存，不再参与业务。 */
    ARCHIVED("归档");

    private final String desc;
}
