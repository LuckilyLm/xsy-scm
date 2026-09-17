package com.xsy.scm.admin.module.business.finance.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会计凭证类型
 *
 * 数据库字段：t_finance_voucher.voucher_type
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum VoucherTypeEnum implements BaseEnum {

    /**
     * 1 收款
     */
    RECEIPT(1, "收款"),

    /**
     * 2 付款
     */
    PAYMENT(2, "付款"),

    /**
     * 3 应收
     */
    RECEIVABLE(3, "应收"),

    /**
     * 4 应付
     */
    PAYABLE(4, "应付"),

    /**
     * 5 费用（运费 / 押金 / 服务费等）
     */
    EXPENSE(5, "费用"),

    ;

    private final Integer value;

    private final String desc;
}
