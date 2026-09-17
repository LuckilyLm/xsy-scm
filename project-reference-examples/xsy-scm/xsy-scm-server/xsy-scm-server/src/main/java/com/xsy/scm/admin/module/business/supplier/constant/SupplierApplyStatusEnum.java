package com.xsy.scm.admin.module.business.supplier.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 供应商商品提报审核状态
 *
 * 数据库字段：t_supplier_product_apply.audit_status
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum SupplierApplyStatusEnum implements BaseEnum {

    /**
     * 1 待审核
     */
    PENDING(1, "待审核"),

    /**
     * 2 已通过
     */
    APPROVED(2, "已通过"),

    /**
     * 3 已驳回
     */
    REJECTED(3, "已驳回"),

    ;

    private final Integer value;

    private final String desc;
}
