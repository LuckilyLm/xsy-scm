package com.xsy.scm.pricing.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 批量定价审计记录的结果。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPriceBatchResultEnum {

    /** 成功。 */
    SUCCESS("成功"),

    /** 失败。 */
    FAILED("失败");

    private final String desc;
}
