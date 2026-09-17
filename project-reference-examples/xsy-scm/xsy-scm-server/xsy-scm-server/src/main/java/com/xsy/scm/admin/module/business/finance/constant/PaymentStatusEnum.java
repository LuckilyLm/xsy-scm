package com.xsy.scm.admin.module.business.finance.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 收款单状态
 *
 * <p>与 docs/requirement/09-财务报表管理.md 收款单状态机保持一致。</p>
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum PaymentStatusEnum implements BaseEnum {

    /**
     * 1 待确认
     */
    PENDING(1, "待确认"),

    /**
     * 2 已确认（已核销应收，终态）
     */
    CONFIRMED(2, "已确认"),

    /**
     * 3 已驳回（终态）
     */
    REJECTED(3, "已驳回"),

    ;

    private final Integer value;

    private final String desc;
}
