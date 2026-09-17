package com.xsy.scm.admin.module.business.stock.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品转换类型
 *
 * 数据库字段：t_product_convert.convert_type
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum ConvertTypeEnum implements BaseEnum {

    /**
     * 1 整件拆零
     */
    SPLIT(1, "整件拆零"),

    /**
     * 2 组合拆分
     */
    COMBINE(2, "组合拆分"),

    ;

    private final Integer value;

    private final String desc;
}
