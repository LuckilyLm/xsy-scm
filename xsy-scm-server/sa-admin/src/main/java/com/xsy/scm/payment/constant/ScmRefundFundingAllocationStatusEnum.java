package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 退款资金来源的分配状态。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmRefundFundingAllocationStatusEnum {

    /** 待处理：已分配，尚未提交渠道。 */
    PENDING("待处理"),

    /** 处理中：已提交资金渠道，等待结果。 */
    PROCESSING("处理中"),

    /** 成功：资金已退回。 */
    SUCCEEDED("成功"),

    /** 失败：渠道拒绝，需人工处理。 */
    FAILED("失败");

    private final String desc;
}
