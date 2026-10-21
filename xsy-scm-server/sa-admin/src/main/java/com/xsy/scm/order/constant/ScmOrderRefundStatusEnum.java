package com.xsy.scm.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单退款单的完成状态。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmOrderRefundStatusEnum {

    /** 待退款：已登记，尚未实际出款。 */
    PENDING("待退款"),

    /** 已退款：款项已退回客户。 */
    COMPLETED("已退款");

    private final String desc;
}
