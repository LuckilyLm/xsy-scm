package com.xsy.scm.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 支付意图状态机。持久化到 {@code payment_intent.status}，与 DDL 的 CHECK 白名单逐字一致。
 *
 * <pre>
 * CREATED ──发起支付──▶ PENDING ──渠道成功──▶ SUCCEEDED   （终态）
 *    │                     │
 *    │                     └──渠道失败──▶ FAILED         （终态）
 *    │                     └──超时未付──▶ EXPIRED        （终态）
 *    └──关闭──────▶ CLOSED （终态，只能从 CREATED / PENDING 进入）
 * </pre>
 *
 * <p>
 * <b>三个终态都不可回退</b>：支付是外部事实，本地改状态改不掉客户已经付过的钱。 需要「撤销」时追加新的支付事实（退款），而不是把意图改回未付。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPaymentIntentStatusEnum {

    /** 已创建、尚未向渠道发起。 */
    CREATED("已创建", false),

    /** 已向渠道发起，等渠道结果。 */
    PENDING("待支付", false),

    /** 渠道确认收款成功。<b>唯一可以派生 Finance 收款事实的状态</b>。 */
    SUCCEEDED("支付成功", true),

    /** 渠道明确失败。 */
    FAILED("支付失败", true),

    /** 超过有效期仍未支付（本地判定，渠道可能也有一条过期回调）。 */
    EXPIRED("已过期", true),

    /** 人工关闭：未支付前放弃这笔意图。 */
    CLOSED("已关闭", true);

    private final String desc;

    /** 终态：不可再转换。 */
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
