package com.xsy.scm.pricing.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 价格变更审计记录的操作类型。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存仍用枚举名。
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
