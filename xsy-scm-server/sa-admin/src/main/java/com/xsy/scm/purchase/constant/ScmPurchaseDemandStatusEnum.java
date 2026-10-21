package com.xsy.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 采购需求的分配状态，由累计已分配数量决定，并与数据库白名单保持一致。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPurchaseDemandStatusEnum {

    /** 待分配：尚未生成任何采购单。 */
    PENDING("待分配"),

    /** 部分分配：已分配数量小于需求数量。 */
    PARTIALLY_ALLOCATED("部分分配"),

    /** 已分配：需求数量已全部落到采购单。 */
    ALLOCATED("已分配");

    private final String desc;
}
