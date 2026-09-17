package com.xsy.scm.admin.module.business.purchase.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 采购明细状态
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum PurchaseItemStatusEnum implements BaseEnum {

    /**
     * 1 待收
     */
    PENDING(1, "待收"),

    /**
     * 2 部分收
     */
    PARTIAL(2, "部分收"),

    /**
     * 3 已收齐
     */
    DONE(3, "已收齐"),

    ;

    private final Integer value;

    private final String desc;
}
