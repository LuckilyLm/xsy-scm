package com.xsy.scm.mall.funds.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商城应收账款的结清状态。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum MallReceivableStateEnum {

    /**
     * 未结清。
     */
    OPEN("未结清"),

    /**
     * 部分结清。
     */
    PARTIAL("部分结清"),

    /**
     * 已结清。
     */
    SETTLED("已结清");

    private final String desc;
}
