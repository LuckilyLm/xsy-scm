package com.xsy.scm.balance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 余额流水的业务来源类型。持久化到 {@code customer_balance_movement.source_type}。
 *
 * <p>
 * 与库上的 {@code ck_customer_balance_movement_source_type} 逐字一致，且**不允许自由填写**：
 * 来源类型一旦可随意填，来源唯一索引就形同虚设 —— 换个名字就能为同一笔钱再充值一次。
 */
@Getter
@RequiredArgsConstructor
public enum ScmBalanceSourceTypeEnum {

    /** 来源是支付域的交易事实：{@code source_id = payment_transaction.id}（3-12b 充值用）。 */
    PAYMENT_TRANSACTION("支付交易"),

    /** 来源是售后退款单：{@code source_id = order_refund.id}（3-12c 余额返还用）。 */
    ORDER_REFUND("售后退款单");

    private final String desc;

    public static boolean isSupported(String value) {
        for (ScmBalanceSourceTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
