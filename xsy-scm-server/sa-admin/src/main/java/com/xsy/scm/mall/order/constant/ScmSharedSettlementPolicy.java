package com.xsy.scm.mall.order.constant;

/**
 * 集团共享钱包的使用权限必须统一校验。没有集团授权的成员不能扣减集团资金。
 */
public final class ScmSharedSettlementPolicy {

    private ScmSharedSettlementPolicy() {
    }

    /**
     * 是否允许子客户在商城直接使用集团共享结算资金（共享钱包 / 集团账期）下单。
     *
     * <p>
     * 判定只看集团主体的授权开关：默认关闭时商城只记录结算约定、不代扣共享资金。 授权是集团主体主动打开的，不能因为客户类型是 GROUP 就默认生效。
     *
     * @param groupGranted
     *            集团主体的 {@code shared_settlement_enabled}
     * @return {@code true} 表示允许代扣
     */
    public static boolean sharedCheckoutAllowed(boolean groupGranted) {
        return groupGranted;
    }
}
