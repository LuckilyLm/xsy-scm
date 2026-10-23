package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 支付意图从创建转为等待渠道结果，最终成功、失败、超时或关闭。终态不能回退；客户已支付的钱只能通过退款形成新记录处理。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPaymentIntentStatusEnum {

    /**
     * 已创建、尚未向渠道发起。
     */
    CREATED("已创建", false),

    /**
     * 已向渠道发起，等渠道结果。
     */
    PENDING("待支付", false),

    /**
     * 渠道确认收款成功。<b>唯一可以派生 Finance 收款事实的状态</b>。
     */
    SUCCEEDED("支付成功", true),

    /**
     * 渠道明确失败。
     */
    FAILED("支付失败", true),

    /**
     * 超过有效期仍未支付（本地判定，渠道可能也有一条过期回调）。
     */
    EXPIRED("已过期", true),

    /**
     * 人工关闭：未支付前放弃这笔意图。
     */
    CLOSED("已关闭", true);

    private final String desc;

    /**
     * 终态：不可再转换。
     */
    private final boolean terminal;

    /**
     * 状态转换表（唯一的转换实现，别在 Service 里再写一份 if）。
     *
     * <p>
     * {@code PENDING} 允许直接回到 {@code CREATED} 吗？不允许 —— 发起过就是发起过， 要重试就新建一个意图，这样「发起了几次」是可数的。
     */
    public static boolean canTransition(String from, String to) {
        ScmPaymentIntentStatusEnum source = of(from);
        ScmPaymentIntentStatusEnum target = of(to);
        if (source == null || source.terminal || target == null) {
            return false;
        }
        return switch (source) {
            case CREATED -> target == PENDING || target == FAILED || target == EXPIRED || target == CLOSED;
            case PENDING -> target == SUCCEEDED || target == FAILED || target == EXPIRED || target == CLOSED;
            default -> false;
        };
    }

    public static ScmPaymentIntentStatusEnum of(String value) {
        for (ScmPaymentIntentStatusEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
