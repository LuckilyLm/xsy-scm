package com.xsy.scm.common.constant;

/**
 * 客户状态：{@code POTENTIAL}（已登记、不可交易）与 {@code COOPERATING}（可交易）—— 客户既可能已登记但尚未合作，也可能允许交易，因此把 {@code ENABLED} 拆成这两个值。
 *
 * <p>
 * <b>唯一判定点</b>：是否允许进入交易链（下单 / 报价 / 结算）只允许通过 {@link #tradable()} 判断，不允许在 Service 里散落
 * {@code "COOPERATING".equals(status)} 这类字符串比较，否则判定规则会漂移。
 */
public enum ScmCustomerStatusEnum {

    /**
     * 潜在客户：已登记，尚未建立合作。
     */
    POTENTIAL,

    /**
     * 合作中：唯一可交易的客户状态。
     */
    COOPERATING,

    /**
     * 暂停合作。
     */
    SUSPENDED,

    /**
     * 黑名单。
     */
    BLACKLIST;

    /**
     * 该状态是否允许进入交易链。
     *
     * <p>
     * 所有交易入口应通过本方法判断资格，避免状态比较在各个服务中漂移。
     */
    public boolean tradable() {
        return this == COOPERATING;
    }
}
