package com.xsy.scm.admin.module.business.supplier.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 供应商对账单状态
 *
 * 数据库字段：t_supplier_statement.status
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum SupplierStatementStatusEnum implements BaseEnum {

    /**
     * 1 待供应商确认
     */
    PENDING(1, "待供应商确认"),

    /**
     * 2 供应商已确认
     */
    CONFIRMED(2, "供应商已确认"),

    /**
     * 3 已结算
     */
    SETTLED(3, "已结算"),

    /**
     * 4 已驳回
     */
    REJECTED(4, "已驳回"),

    ;

    private final Integer value;

    private final String desc;
}
