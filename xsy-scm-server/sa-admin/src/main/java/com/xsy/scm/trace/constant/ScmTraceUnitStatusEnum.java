package com.xsy.scm.trace.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 追溯码单元状态，与 {@code xsy_trace_unit} 的 CHECK 约束逐值对应。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmTraceUnitStatusEnum {

    /**
     * 在用：可绑定订单行，也可发运。
     */
    ACTIVE("在用"),

    /**
     * 已作废：绑定关系已解除，只保留追溯记录。
     */
    VOID("已作废");

    private final String desc;
}
