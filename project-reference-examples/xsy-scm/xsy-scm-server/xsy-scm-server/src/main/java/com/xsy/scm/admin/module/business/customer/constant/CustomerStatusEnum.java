package com.xsy.scm.admin.module.business.customer.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户状态
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum CustomerStatusEnum implements BaseEnum {

    /**
     * 1 潜在
     */
    POTENTIAL(1, "潜在"),

    /**
     * 2 合作中
     */
    COOPERATING(2, "合作中"),

    /**
     * 3 暂停合作
     */
    SUSPENDED(3, "暂停合作"),

    /**
     * 4 黑名单
     */
    BLACKLIST(4, "黑名单"),

    ;

    private final Integer value;

    private final String desc;
}
