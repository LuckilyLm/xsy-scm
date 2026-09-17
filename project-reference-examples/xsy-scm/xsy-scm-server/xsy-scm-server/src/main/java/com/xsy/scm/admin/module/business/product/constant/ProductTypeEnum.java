package com.xsy.scm.admin.module.business.product.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品类型：标品 / 非标品
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum ProductTypeEnum implements BaseEnum {

    /**
     * 1 标品（规格固定，可按件计量）
     */
    STANDARD(1, "标品"),

    /**
     * 2 非标品（按实际重量计量）
     */
    NON_STANDARD(2, "非标品"),

    ;

    private final Integer value;

    private final String desc;
}
