package com.xsy.scm.admin.module.business.finance.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 外部系统类型
 *
 * 数据库字段：t_external_config.system_type
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum ExternalSystemTypeEnum implements BaseEnum {

    /**
     * 1 金蝶云星空
     */
    KINGDEE_STAR(1, "金蝶云星空"),

    /**
     * 2 金蝶云星瀚
     */
    KINGDEE_GALAXY(2, "金蝶云星瀚"),

    /**
     * 3 用友 T+
     */
    YONYOU_T_PLUS(3, "用友T+"),

    /**
     * 4 用友 U8
     */
    YONYOU_U8(4, "用友U8"),

    /**
     * 5 溯源平台（政府监管）
     */
    TRACE_PLATFORM(5, "溯源平台"),

    /**
     * 6 团餐平台
     */
    CANTEEN_PLATFORM(6, "团餐平台"),

    ;

    private final Integer value;

    private final String desc;
}
