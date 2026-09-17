package com.xsy.scm.admin.module.business.trace.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 溯源批次状态
 *
 * 数据库字段：t_trace_batch.status
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum TraceBatchStatusEnum implements BaseEnum {

    /**
     * 1 有效
     */
    VALID(1, "有效"),

    /**
     * 2 已过期
     */
    EXPIRED(2, "已过期"),

    /**
     * 3 已作废
     */
    INVALID(3, "已作废"),

    ;

    private final Integer value;

    private final String desc;
}
