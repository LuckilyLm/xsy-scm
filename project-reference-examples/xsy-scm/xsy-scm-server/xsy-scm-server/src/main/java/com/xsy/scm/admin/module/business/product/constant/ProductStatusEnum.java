package com.xsy.scm.admin.module.business.product.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品状态
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum ProductStatusEnum implements BaseEnum {

    /**
     * 1 草稿
     */
    DRAFT(1, "草稿"),

    /**
     * 2 待上架
     */
    PENDING(2, "待上架"),

    /**
     * 3 已上架
     */
    ON_SALE(3, "已上架"),

    /**
     * 4 已下架
     */
    OFF_SALE(4, "已下架"),

    /**
     * 5 已作废
     */
    INVALID(5, "已作废"),

    ;

    private final Integer value;

    private final String desc;
}
