package com.xsy.scm.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通用启用状态。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmEnableStatusEnum {

    /**
     * 启用。
     */
    ENABLED("启用"),

    /**
     * 停用。
     */
    DISABLED("停用");

    private final String desc;
}
