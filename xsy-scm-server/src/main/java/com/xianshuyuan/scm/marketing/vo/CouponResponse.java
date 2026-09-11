package com.xianshuyuan.scm.marketing.vo;

import com.xianshuyuan.scm.marketing.entity.CouponType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 优惠券模板视图。剩余可领数量由 total_quantity - issued_quantity 推导。
 */
public record CouponResponse(Long id, String name, CouponType couponType, BigDecimal thresholdAmount,
                             BigDecimal discountRate, BigDecimal reduceAmount, Integer totalQuantity,
                             Integer issuedQuantity, Integer perLimit, Integer remainQuantity,
                             OffsetDateTime validFrom, OffsetDateTime validTo, String status) {
}
