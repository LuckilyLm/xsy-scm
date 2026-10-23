package com.xsy.scm.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 客户退货申请的处理状态。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmOrderReturnStatusEnum {

    /**
     * 待审核：客户已提交，等待仓库处理。
     */
    PENDING("待审核"),

    /**
     * 已批准：可继续收货与退款。
     */
    APPROVED("已批准"),

    /**
     * 已驳回：不进入收货流程，需说明原因。
     */
    REJECTED("已驳回"),

    /**
     * 已取消：申请方撤回。
     */
    CANCELLED("已取消");

    private final String desc;
}
