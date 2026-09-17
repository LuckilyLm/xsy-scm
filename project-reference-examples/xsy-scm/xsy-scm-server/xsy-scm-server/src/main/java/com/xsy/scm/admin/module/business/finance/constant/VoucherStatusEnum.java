package com.xsy.scm.admin.module.business.finance.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会计凭证状态
 *
 * 数据库字段：t_finance_voucher.status
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum VoucherStatusEnum implements BaseEnum {

    /**
     * 1 已生成
     */
    GENERATED(1, "已生成"),

    /**
     * 2 已作废
     */
    INVALID(2, "已作废"),

    ;

    private final Integer value;

    private final String desc;
}
