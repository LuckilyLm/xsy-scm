package com.xsy.scm.pricing.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 协议价与客户类型价审计表记录的操作类型。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPriceOperationTypeEnum {

    /** 新增。 */
    CREATE("新增"),

    /** 修改。 */
    UPDATE("修改"),

    /** 删除。 */
    DELETE("删除");

    private final String desc;
}
