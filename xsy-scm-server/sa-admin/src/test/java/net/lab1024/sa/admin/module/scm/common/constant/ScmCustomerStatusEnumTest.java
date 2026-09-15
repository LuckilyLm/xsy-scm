package net.lab1024.sa.admin.module.scm.common.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户「可交易」判定唯一性门禁。
 *
 * <p>W3 引入订单域后，是否允许下单必须调用 {@link ScmCustomerStatusEnum#tradable()}，
 * 不允许在业务代码里散落字符串比较。本测试锁定只有 {@code COOPERATING} 可交易。
 */
class ScmCustomerStatusEnumTest {

    @Test
    @DisplayName("四态齐备，取值域与 DB CHECK 一致")
    void exposesExactlyFourStates() {
        assertThat(ScmCustomerStatusEnum.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("POTENTIAL", "COOPERATING", "SUSPENDED", "BLACKLIST");
    }

    @Test
    @DisplayName("只有 COOPERATING 可交易")
    void onlyCooperatingIsTradable() {
        assertThat(ScmCustomerStatusEnum.COOPERATING.tradable()).isTrue();
        assertThat(ScmCustomerStatusEnum.POTENTIAL.tradable()).isFalse();
        assertThat(ScmCustomerStatusEnum.SUSPENDED.tradable()).isFalse();
        assertThat(ScmCustomerStatusEnum.BLACKLIST.tradable()).isFalse();
    }

    @Test
    @DisplayName("可交易状态恰好一个：防止后续顺手放宽成多个")
    void exactlyOneTradableState() {
        long tradable = java.util.Arrays.stream(ScmCustomerStatusEnum.values())
                .filter(ScmCustomerStatusEnum::tradable)
                .count();
        assertThat(tradable).isEqualTo(1L);
    }
}
