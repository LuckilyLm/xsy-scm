package com.xsy.scm.delivery.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 配送打印工作流的订单级筛选条件。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmDeliveryOrderPrintFilterEnum {

    /** 全部订单。 */
    ALL("全部"),

    /** 已打印。 */
    PRINTED("已打印"),

    /** 未打印。 */
    UNPRINTED("未打印");

    private final String desc;
}
