package net.lab1024.sa.admin.module.scm.common.constant;

/**
 * 客户状态。
 *
 * <p>legacy 只有二态（{@code ENABLED} / {@code DISABLED}），无法表达「登记但尚未合作」。
 * W2 采用四态（Target Design Q1），因此 {@code ENABLED} 语义被拆成
 * {@code POTENTIAL}（已登记、不可交易）与 {@code COOPERATING}（可交易）。
 *
 * <p><b>唯一判定点</b>：是否允许进入交易链（下单 / 报价 / 结算）只允许通过 {@link #tradable()} 判断，
 * 不允许在 Service 里散落 {@code "COOPERATING".equals(status)} 这类字符串比较——否则 W3 引入
 * 订单域时判定规则会漂移。
 */
public enum ScmCustomerStatusEnum {

    /** 潜在客户：已登记，尚未建立合作。 */
    POTENTIAL,

    /** 合作中：唯一可交易的客户状态。 */
    COOPERATING,

    /** 暂停合作。 */
    SUSPENDED,

    /** 黑名单。 */
    BLACKLIST;

    /**
     * 该状态是否允许进入交易链。
     *
     * <p>W2 没有订单域，因此当前没有任何生产调用方；方法先落地并测试锁定，供 W3 直接复用。
     */
    public boolean tradable() {
        return this == COOPERATING;
    }
}
