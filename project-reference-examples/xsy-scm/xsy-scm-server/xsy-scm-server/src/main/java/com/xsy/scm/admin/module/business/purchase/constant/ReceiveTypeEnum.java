package com.xsy.scm.admin.module.business.purchase.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 收货类型
 *
 * <p>对标蔬东坡 17.0 / 17.4：现场收货可不关联采购单（无单收货），后续可补关联。</p>
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum ReceiveTypeEnum implements BaseEnum {

    /**
     * 1 采购收货（关联采购单 / 明细）
     */
    PURCHASE(1, "采购收货"),

    /**
     * 2 无单收货（现场收货，采购单 / 明细为空）
     */
    NO_ORDER(2, "无单收货"),

    ;

    private final Integer value;

    private final String desc;
}
