package com.xianshuyuan.scm.marketing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 发放优惠券请求。quantity 为本次发放张数，受总量与每人限领约束。
 */
public record CouponIssueRequest(
        @NotNull(message = "优惠券不能为空") Long couponId,
        @NotNull(message = "客户不能为空") Long customerId,
        @Positive(message = "发放张数必须大于零") Integer quantity
) {
}
