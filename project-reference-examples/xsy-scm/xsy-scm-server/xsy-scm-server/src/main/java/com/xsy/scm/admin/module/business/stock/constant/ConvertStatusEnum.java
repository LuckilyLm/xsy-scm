package com.xsy.scm.admin.module.business.stock.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品转换单状态
 *
 * 数据库字段：t_product_convert.status
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum ConvertStatusEnum implements BaseEnum {

    /**
     * 1 待审核
     */
    PENDING(1, "待审核"),

    /**
     * 2 已完成
     */
    COMPLETED(2, "已完成"),

    /**
     * 3 已驳回
     */
    REJECTED(3, "已驳回"),

    ;

    private final Integer value;

    private final String desc;
}
