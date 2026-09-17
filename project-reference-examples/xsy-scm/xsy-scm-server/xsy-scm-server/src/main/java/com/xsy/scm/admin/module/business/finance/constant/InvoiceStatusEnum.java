package com.xsy.scm.admin.module.business.finance.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发票状态
 *
 * 数据库字段：t_invoice.status
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum InvoiceStatusEnum implements BaseEnum {

    /**
     * 1 可开票
     */
    INVOICEABLE(1, "可开票"),

    /**
     * 2 已开票
     */
    INVOICED(2, "已开票"),

    /**
     * 3 已红冲
     */
    RED_FLUSHED(3, "已红冲"),

    ;

    private final Integer value;

    private final String desc;
}
