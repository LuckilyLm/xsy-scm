package com.xsy.scm.admin.module.business.customer.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户折扣率 状态
 *
 * 数据库字段：t_customer_discount.status
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum DiscountStatusEnum implements BaseEnum {

    /**
     * 1 生效
     */
    EFFECTIVE(1, "生效"),

    /**
     * 2 停用
     */
    DISABLED(2, "停用"),

    ;

    private final Integer value;

    private final String desc;
}
