package com.xianshuyuan.scm.marketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 新建 / 修改优惠券模板请求。
 */
public record CouponSaveRequest(
        @NotBlank(message = "券名称不能为空") @Size(max = 100, message = "券名称过长") String name,
        @NotBlank(message = "券类型不能为空") @Size(max = 16) String couponType,
        @PositiveOrZero(message = "门槛金额不能为负") BigDecimal thresholdAmount,
        @PositiveOrZero(message = "折扣率不能为负") BigDecimal discountRate,
        @PositiveOrZero(message = "减免金额不能为负") BigDecimal reduceAmount,
        @PositiveOrZero(message = "发放总量不能为负") Integer totalQuantity,
        @PositiveOrZero(message = "每人限领不能为负") Integer perLimit,
        OffsetDateTime validFrom,
        OffsetDateTime validTo,
        @Size(max = 16) String status
) {
}
