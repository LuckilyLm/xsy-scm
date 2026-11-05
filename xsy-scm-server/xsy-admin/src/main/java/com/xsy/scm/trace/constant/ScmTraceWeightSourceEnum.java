package com.xsy.scm.trace.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 包装码净重的来源。
 *
 * <p>
 * 与收货称重的来源分开：包装码要区分「后台补录」和「电子秤称重」，扫码页据此说明重量来源。
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
