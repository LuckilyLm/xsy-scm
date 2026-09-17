package com.xsy.scm.admin.module.business.product.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 采购方式
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum PurchaseModeEnum implements BaseEnum {

    /**
     * 1 自采
     */
    SELF(1, "自采"),

    /**
     * 2 供应商送货
     */
    SUPPLIER(2, "供应商送货"),

    ;

    private final Integer value;

    private final String desc;
}
