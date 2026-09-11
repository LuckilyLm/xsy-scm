package com.xianshuyuan.scm.marketing.vo;

import com.xianshuyuan.scm.marketing.entity.CouponStatus;

import java.time.OffsetDateTime;

/**
 * 客户优惠券视图（发放记录）。
 */
public record CustomerCouponResponse(Long id, Long couponId, Long customerId, String couponNo,
                                     CouponStatus status, Long usedOrderId, OffsetDateTime usedAt,
                                     OffsetDateTime obtainedAt, OffsetDateTime validFrom,
                                     OffsetDateTime validTo) {
}
