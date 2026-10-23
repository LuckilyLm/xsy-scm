package com.xsy.scm.delivery.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 排线建议的独立处理状态，不代替配送线路状态。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmDeliveryPlanStatusEnum {

    /** 已建议：系统或人工给出排线方案，尚未采用。 */
    PROPOSED("已建议"),

    /** 已采用：方案已生成实际线路。 */
    APPLIED("已采用"),

    /** 已废弃：方案不再使用。 */
    DISCARDED("已废弃");

    private final String desc;
}
