package com.xsy.scm.delivery.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 签收结果。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmDeliverySignResultEnum {

    /** 正常签收。 */
    SIGNED("正常签收"),

    /** 异常签收：需填写原因并跟进。 */
    EXCEPTION("异常签收");

    private final String desc;
}
