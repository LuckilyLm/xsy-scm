package com.xsy.scm.admin.module.business.purchase.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 收货单状态
 *
 * <p>变为「已入库(2)」后生成应付（09-02 / 05-07 已定）。</p>
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum ReceiveStatusEnum implements BaseEnum {

    /**
     * 1 已收
     */
    RECEIVED(1, "已收"),

    /**
     * 2 已入库
     */
    STOCKED(2, "已入库"),

    /**
     * 3 已作废
     */
    INVALID(3, "已作废"),

    ;

    private final Integer value;

    private final String desc;
}
