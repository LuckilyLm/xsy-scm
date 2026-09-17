package com.xsy.scm.admin.module.business.supplier.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 供应商账号状态
 *
 * 数据库字段：t_supplier_account.status
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum SupplierAccountStatusEnum implements BaseEnum {

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
