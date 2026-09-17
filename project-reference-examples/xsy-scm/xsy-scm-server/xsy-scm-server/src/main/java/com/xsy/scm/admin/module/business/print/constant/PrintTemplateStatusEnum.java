package com.xsy.scm.admin.module.business.print.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 打印模板 状态
 *
 * 数据库字段：t_print_template.status
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum PrintTemplateStatusEnum implements BaseEnum {

    /**
     * 1 启用
     */
    ENABLED(1, "启用"),

    /**
     * 2 停用
     */
    DISABLED(2, "停用"),

    ;

    private final Integer value;

    private final String desc;
}
