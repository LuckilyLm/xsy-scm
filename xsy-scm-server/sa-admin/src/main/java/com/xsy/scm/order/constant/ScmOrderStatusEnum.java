package com.xsy.scm.order.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 销售订单状态，与订单表的 CHECK 约束逐值对应。
 *
 * <p>
 * {@code desc} 供页面、导出与打印使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmOrderStatusEnum {

    /** 草稿：尚未提交，可自由修改。 */
    DRAFT("草稿"),

    /** 待确认：已提交，等待仓库确认。 */
    PENDING("待确认"),

    /** 已确认：进入分拣与出库流程。 */
    CONFIRMED("已确认"),

    /** 已取消：不再参与分拣与配送。 */
    CANCELLED("已取消");

    private final String desc;
}
