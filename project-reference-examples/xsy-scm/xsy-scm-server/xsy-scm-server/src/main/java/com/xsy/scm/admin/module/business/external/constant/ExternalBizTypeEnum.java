package com.xsy.scm.admin.module.business.external.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 外部平台映射对象类型
 *
 * 数据库字段：t_external_mapping.biz_type
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum ExternalBizTypeEnum implements BaseEnum {

    /**
     * 1 商品
     */
    PRODUCT(1, "商品"),

    /**
     * 2 客户
     */
    CUSTOMER(2, "客户"),

    /**
     * 3 供应商
     */
    SUPPLIER(3, "供应商"),

    ;

    private final Integer value;

    private final String desc;
}
