package com.xsy.scm.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 客户状态：新建即视为合作中；只有 {@code COOPERATING} 才能交易，所以没直接复用通用的 {@code ENABLED}。
 *
 * <p>
 * 是否允许进入交易链（下单 / 报价 / 结算）只通过 {@link #tradable()} 判断，不要在 Service 里散落 {@code "COOPERATING".equals(status)} 这类字符串比较。
 */
@Getter
@RequiredArgsConstructor
public enum ScmCustomerStatusEnum {

    /** 合作中：唯一可交易的客户状态，也是新建客户的初始状态。 */
    COOPERATING("合作中"),

    /** 暂停合作。 */
    SUSPENDED("暂停合作"),

    /** 黑名单。 */
    BLACKLIST("黑名单");

    /** 状态的中文名称，用于错误提示直接告诉用户当前是哪个状态。 */
    private final String desc;

    /** 该状态是否允许进入交易链，交易入口统一用这个方法判断。 */
    public boolean tradable() {
        return this == COOPERATING;
    }
}
