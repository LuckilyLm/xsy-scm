package com.xsy.scm.admin.module.business.external.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 外部平台映射状态
 *
 * 数据库字段：t_external_mapping.status
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum MappingStatusEnum implements BaseEnum {

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
