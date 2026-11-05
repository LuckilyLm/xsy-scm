package com.xsy.scm.trace.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 追溯码更正申请状态，与 {@code xsy_trace_unit_correction} 的 CHECK 约束逐值对应。
 *
 * <p>
 * 申请与审批是同一行记录的状态流转，因此没有「已撤销」：驳回后要改只能重新申请。 {@code desc} 供页面与导出使用，保存时仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmTraceCorrectionStatusEnum {

    /**
     * 待审批：同一单元同一时刻最多一条。
     */
    PENDING("待审批"),

    /**
     * 已批准：绑定已解除，解绑事件已追加。
     */
    APPROVED("已批准"),

    /**
     * 已驳回：绑定继续有效，只留审批意见。
     */
    REJECTED("已驳回");

    private final String desc;
}
