package com.xsy.scm.mall.order.constant;

/** 集团共享钱包的使用权限必须统一校验。没有集团授权的成员不能扣减集团资金。 */
public final class ScmSharedSettlementPolicy {

    private ScmSharedSettlementPolicy() {
    }

    /**
     * 是否允许客户在商城直接使用集团共享结算资金（共享钱包 / 集团账期）下单。
     *
     * @return 当前恒为 {@code false}，即共享结算下商城只记录约定、不代扣共享资金
     */
    public static boolean sharedCheckoutAllowed() {
        return false;
    }
}
