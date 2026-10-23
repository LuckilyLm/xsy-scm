package com.xsy.scm.mall.promotion.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 「我的优惠券」里的一张客户券。
 *
 * <p>
 * {@code effectiveStatus} 由服务端按实例状态与模板有效期派生： {@code AVAILABLE / USED / EXPIRED / RELEASED}，客户端按它分 Tab 展示，不自行判断过期。
 */
@Data
public class MallCouponInstanceVO {

    private Long id;

    private Long couponId;

    private String couponName;

    private String discountType;

    private String discountTypeLabel;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal discountValue;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal minOrderAmount;

    private OffsetDateTime validTo;

    /**
     * 实例原始状态：{@code AVAILABLE / RESERVED / USED / RELEASED}。
     */
    private String status;

    private String effectiveStatus;

    private Long usedOrderId;

    private OffsetDateTime usedAt;
}
