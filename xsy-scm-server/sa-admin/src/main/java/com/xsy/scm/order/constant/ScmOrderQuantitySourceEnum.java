package com.xsy.scm.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单行数量的来源。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmOrderQuantitySourceEnum {

    /**
     * 系统按规则计算。
     */
    SYSTEM("系统计算"),

    /**
     * 人工录入。
     */
    MANUAL("人工录入");

    private final String desc;
}
