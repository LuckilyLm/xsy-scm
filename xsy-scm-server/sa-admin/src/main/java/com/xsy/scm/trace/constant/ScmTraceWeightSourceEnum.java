package com.xsy.scm.trace.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 包装码净重的来源。
 *
 * <p>
 * 与收货称重的来源分开：收货侧只有人工与设备两种，包装码还要表达「后台补录」与「电子秤称重」的区别， 扫码页据此说明重量是怎么来的，不能共用一个枚举掩盖语义差异。
 */
@Getter
@RequiredArgsConstructor
public enum ScmTraceWeightSourceEnum {

    /**
     * 电子秤称重，随分拣筐一起产生。
     */
    SCALE("电子秤"),

    /**
     * 后台人工补录，必须写明原因。
     */
    MANUAL("人工补录");

    private final String desc;
}
