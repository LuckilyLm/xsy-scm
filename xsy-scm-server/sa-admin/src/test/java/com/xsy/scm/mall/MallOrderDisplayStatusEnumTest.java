package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.mall.order.constant.MallOrderDisplayStatusEnum;
import org.junit.jupiter.api.Test;

/**
 * 展示分类只是「订单状态 + 配送履约状态」的投影：不写库、不参与状态转换，但派生规则必须稳定。
 */
class MallOrderDisplayStatusEnumTest {

    @Test
    void cancelledOrderStaysCancelledRegardlessOfFulfillment() {
        assertThat(MallOrderDisplayStatusEnum.derive("CANCELLED", null))
                .isEqualTo(MallOrderDisplayStatusEnum.CANCELLED);
        assertThat(MallOrderDisplayStatusEnum.derive("CANCELLED", "IN_TRANSIT"))
                .isEqualTo(MallOrderDisplayStatusEnum.CANCELLED);
    }

    @Test
    void unconfirmedOrdersArePending() {
        assertThat(MallOrderDisplayStatusEnum.derive("DRAFT", null)).isEqualTo(MallOrderDisplayStatusEnum.PENDING);
        assertThat(MallOrderDisplayStatusEnum.derive("PENDING", null)).isEqualTo(MallOrderDisplayStatusEnum.PENDING);
    }

    @Test
    void confirmedOrderFollowsTheFulfillmentFact() {
        assertThat(MallOrderDisplayStatusEnum.derive("CONFIRMED", null))
                .isEqualTo(MallOrderDisplayStatusEnum.PREPARING);
        assertThat(MallOrderDisplayStatusEnum.derive("CONFIRMED", "PENDING"))
                .isEqualTo(MallOrderDisplayStatusEnum.PREPARING);
        assertThat(MallOrderDisplayStatusEnum.derive("CONFIRMED", "IN_TRANSIT"))
                .isEqualTo(MallOrderDisplayStatusEnum.DELIVERING);
        assertThat(MallOrderDisplayStatusEnum.derive("CONFIRMED", "SIGNED"))
                .isEqualTo(MallOrderDisplayStatusEnum.COMPLETED);
        // 异常签收（含拒收）也是终态，原因在详情里展示
        assertThat(MallOrderDisplayStatusEnum.derive("CONFIRMED", "EXCEPTION"))
                .isEqualTo(MallOrderDisplayStatusEnum.COMPLETED);
    }

    @Test
    void unknownOrderStatusFallsBackToPendingInsteadOfFailingTheWholeList() {
        assertThat(MallOrderDisplayStatusEnum.derive("SOMETHING_NEW", null))
                .isEqualTo(MallOrderDisplayStatusEnum.PENDING);
        assertThat(MallOrderDisplayStatusEnum.derive(null, null)).isEqualTo(MallOrderDisplayStatusEnum.PENDING);
    }

    @Test
    void onlyPendingOrdersMayBeOfferedAsCancelable() {
        assertThat(MallOrderDisplayStatusEnum.PENDING.cancelable()).isTrue();
        assertThat(MallOrderDisplayStatusEnum.PREPARING.cancelable()).isFalse();
        assertThat(MallOrderDisplayStatusEnum.DELIVERING.cancelable()).isFalse();
        assertThat(MallOrderDisplayStatusEnum.COMPLETED.cancelable()).isFalse();
        assertThat(MallOrderDisplayStatusEnum.CANCELLED.cancelable()).isFalse();
    }
}
