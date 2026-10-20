package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.mall.order.constant.ScmSharedSettlementPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 集团共享结算的授权判定。
 *
 * <p>
 * 默认关闭是这条规则的重点：过去商城只记录结算约定、不代扣共享资金， 打开开关必须是集团主体的显式动作，不能因为客户类型是 GROUP 就自动生效。
 */
class ScmSharedSettlementPolicyTest {

    @Test
    @DisplayName("未授权时不代扣集团资金")
    void deniedWithoutGrant() {
        assertThat(ScmSharedSettlementPolicy.sharedCheckoutAllowed(false)).isFalse();
    }

    @Test
    @DisplayName("集团主体授权后才允许代扣")
    void allowedWithGrant() {
        assertThat(ScmSharedSettlementPolicy.sharedCheckoutAllowed(true)).isTrue();
    }
}
