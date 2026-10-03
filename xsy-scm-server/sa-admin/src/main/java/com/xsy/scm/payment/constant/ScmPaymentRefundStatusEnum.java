package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 退款状态。持久化到 {@code payment_refund.status}。 */
@Getter
@RequiredArgsConstructor
public enum ScmPaymentRefundStatusEnum {

    /** 本地已建退款单，尚未向渠道发起。 */
    CREATED("已创建", false),

    /** 已向渠道发起，等结果。 */
    PROCESSING("退款处理中", false),

    SUCCEEDED("退款成功", true),

    FAILED("退款失败", true),

    /** 人工关闭：发起前放弃。 */
    CLOSED("已关闭", true);

    private final String desc;

    private final boolean terminal;

    /** 唯一转换实现。失败后要重试就新建一条退款，保留「试过几次」。 */
    public static boolean canTransition(String from, String to) {
        ScmPaymentRefundStatusEnum source = of(from);
        ScmPaymentRefundStatusEnum target = of(to);
        if (source == null || source.terminal || target == null) {
            return false;
        }
        return switch (source) {
            case CREATED -> target == PROCESSING || target == FAILED || target == CLOSED;
            case PROCESSING -> target == SUCCEEDED || target == FAILED;
            default -> false;
        };
    }

    public static ScmPaymentRefundStatusEnum of(String value) {
        for (ScmPaymentRefundStatusEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
