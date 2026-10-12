package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.mall.aftersale.constant.MallAfterSaleDisplayStatusEnum;
import org.junit.jupiter.api.Test;

/**
 * 售后展示分类按退货单与实际资金完成事实派生，未知状态不猜成「退款完成」。
 */
class MallAfterSaleDisplayStatusEnumTest {

    @Test
    void pendingReturnIsReviewing() {
        assertThat(MallAfterSaleDisplayStatusEnum.derive("PENDING", false, false))
                .isEqualTo(MallAfterSaleDisplayStatusEnum.REVIEWING);
    }

    @Test
    void rejectedAndCancelledKeepTheirOwnBuckets() {
        assertThat(MallAfterSaleDisplayStatusEnum.derive("REJECTED", false, false))
                .isEqualTo(MallAfterSaleDisplayStatusEnum.REJECTED);
        assertThat(MallAfterSaleDisplayStatusEnum.derive("CANCELLED", false, false))
                .isEqualTo(MallAfterSaleDisplayStatusEnum.CANCELLED);
    }

    @Test
    void approvedWithoutRefundMeansNothingToRefund() {
        // 退款净额为 0 时没有退款单：售后已终结，不能停在「退款中」
        assertThat(MallAfterSaleDisplayStatusEnum.derive("APPROVED", false, false))
                .isEqualTo(MallAfterSaleDisplayStatusEnum.REFUNDED);
    }

    @Test
    void approvedWithPendingRefundIsRefunding() {
        assertThat(MallAfterSaleDisplayStatusEnum.derive("APPROVED", true, false))
                .isEqualTo(MallAfterSaleDisplayStatusEnum.REFUNDING);
    }

    @Test
    void approvedWithCompletedFundingIsRefunded() {
        assertThat(MallAfterSaleDisplayStatusEnum.derive("APPROVED", true, true))
                .isEqualTo(MallAfterSaleDisplayStatusEnum.REFUNDED);
    }

    @Test
    void unknownStatusFallsBackToReviewing() {
        // 未知的退货状态宁可显示「审核中」，也不能让客户以为钱已经退了
        assertThat(MallAfterSaleDisplayStatusEnum.derive("SOMETHING_NEW", false, false))
                .isEqualTo(MallAfterSaleDisplayStatusEnum.REVIEWING);
    }
}
