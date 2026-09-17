package com.xsy.scm.admin.module.business.purchase.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 询价单状态
 *
 * 数据库字段：t_inquiry.status
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum InquiryStatusEnum implements BaseEnum {

    /**
     * 1 待报价
     */
    PENDING(1, "待报价"),

    /**
     * 2 报价中
     */
    QUOTING(2, "报价中"),

    /**
     * 3 已完成
     */
    COMPLETED(3, "已完成"),

    /**
     * 4 已取消
     */
    CANCELLED(4, "已取消"),

    ;

    private final Integer value;

    private final String desc;
}
