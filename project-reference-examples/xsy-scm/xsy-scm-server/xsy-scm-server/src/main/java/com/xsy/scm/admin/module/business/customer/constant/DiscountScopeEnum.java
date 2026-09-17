package com.xsy.scm.admin.module.business.customer.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户折扣率 范围
 *
 * 数据库字段：t_customer_discount.scope_type
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum DiscountScopeEnum implements BaseEnum {

    /**
     * 1 统一折扣（客户维度）
     */
    UNIFIED(1, "统一折扣"),

    /**
     * 2 按商品
     */
    PRODUCT(2, "按商品"),

    /**
     * 3 按分类
     */
    CATEGORY(3, "按分类"),

    ;

    private final Integer value;

    private final String desc;
}
