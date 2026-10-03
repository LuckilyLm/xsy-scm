package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 交易状态。持久化到 {@code payment_transaction.status}。
 *
 * <p>
 * 与意图状态分开：意图是「打算收多少」，交易是「渠道那一次流水」。
 * 一个意图可能先失败一次、再成功一次（用户换了支付方式），因此交易是多条、
 * 意图只有一个最终状态。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPaymentTransactionStatusEnum {

    PENDING("待支付", false),

    SUCCEEDED("支付成功", true),

    FAILED("支付失败", true),

    CLOSED("已关闭", true);

    private final String desc;

    private final boolean terminal;

    public static ScmPaymentTransactionStatusEnum of(String value) {
        for (ScmPaymentTransactionStatusEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
