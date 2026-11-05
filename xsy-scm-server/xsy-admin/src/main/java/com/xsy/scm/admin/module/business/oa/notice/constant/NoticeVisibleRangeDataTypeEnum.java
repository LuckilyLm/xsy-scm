package com.xsy.scm.admin.module.business.oa.notice.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.xsy.scm.base.common.enumeration.BaseEnum;

/**
 * 公告、通知 可见范围类型
 *
 */
@Getter
@AllArgsConstructor
public enum NoticeVisibleRangeDataTypeEnum implements BaseEnum {

    /**
     * 员工
     */
    EMPLOYEE(1, "员工"),

    /**
     * 部门
     */
    DEPARTMENT(2, "部门"),

    ;

    private final Integer value;

    private final String desc;
}
