package com.xsy.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 客户退款付款方式独立于供应商付款。退款方式必须表示真实退付资金的渠道，不能把余额抵扣混同于已经发生的付款。 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceCustomerRefundMethodEnum {

    /** 现金退还。 */
    CASH("现金"),

    /** 银行转账退还。 */
    BANK_TRANSFER("银行转账"),

    /** 在线支付原路退回。 */
    ONLINE_PAYMENT("在线支付"),

    /** 混合资金按原来源返还后的聚合财务事实。 */
    ORIGINAL_FUNDING("原资金返还"),

    /** 其它。 */
    OTHER("其它");

    private final String desc;

    public static boolean isSupported(String value) {
        for (ScmFinanceCustomerRefundMethodEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static ScmFinanceCustomerRefundMethodEnum of(String value) {
        for (ScmFinanceCustomerRefundMethodEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
