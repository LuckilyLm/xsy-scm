package com.xsy.scm.admin.module.business.finance.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 凭证分录 借贷方向
 *
 * 数据库字段：t_voucher_entry.direction
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum VoucherEntryDirectionEnum implements BaseEnum {

    /**
     * 1 借
     */
    DEBIT(1, "借"),

    /**
     * 2 贷
     */
    CREDIT(2, "贷"),

    ;

    private final Integer value;

    private final String desc;
}
