package com.xsy.scm.admin.module.business.trace.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 溯源码状态
 *
 * 数据库字段：t_trace_code.status
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum TraceCodeStatusEnum implements BaseEnum {

    /**
     * 1 未启用
     */
    CREATED(1, "未启用"),

    /**
     * 2 已启用
     */
    ENABLED(2, "已启用"),

    /**
     * 3 已作废
     */
    INVALID(3, "已作废"),

    ;

    private final Integer value;

    private final String desc;
}
