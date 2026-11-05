package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 退款回收（把已退款项从原收款里冲回）的处理结果。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存时仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmRefundRecoveryResultEnum {

    STARTED("已发起"),

    SUCCEEDED("成功"),

    PENDING("待处理"),

    FAILED("失败"),

    /** 金额不符：回收金额与应回收金额不一致。 */
    AMOUNT_MISMATCH("金额不符"),

    /** 异常：需人工核查。 */
    ERROR("异常");

    private final String desc;
}
