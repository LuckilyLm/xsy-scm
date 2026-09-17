package com.xsy.scm.admin.module.business.finance.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应收状态
 *
 * <p>与 docs/requirement/09-财务报表管理.md 应收状态机保持一致。</p>
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum ReceivableStatusEnum implements BaseEnum {

    /**
     * 1 待收款
     */
    PENDING(1, "待收款"),

    /**
     * 2 部分收款
     */
    PARTIAL(2, "部分收款"),

    /**
     * 3 已结清（终态）
     */
    SETTLED(3, "已结清"),

    /**
     * 4 已冲销（退款 / 坏账，终态）
     */
    WRITTEN_OFF(4, "已冲销"),

    ;

    private final Integer value;

    private final String desc;
}
