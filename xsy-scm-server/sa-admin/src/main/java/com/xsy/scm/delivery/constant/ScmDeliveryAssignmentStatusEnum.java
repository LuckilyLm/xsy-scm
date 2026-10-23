package com.xsy.scm.delivery.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 线路与订单的指派状态，保留用于配送历史追溯。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmDeliveryAssignmentStatusEnum {

    /**
     * 占用中：订单当前归属该线路。
     */
    ACTIVE("占用中"),

    /**
     * 已释放：订单已移出该线路。
     */
    RELEASED("已释放");

    private final String desc;
}
