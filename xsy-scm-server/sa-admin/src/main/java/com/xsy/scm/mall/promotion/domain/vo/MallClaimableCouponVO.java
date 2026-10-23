package com.xsy.scm.mall.promotion.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 商城领券中心的一张可领券模板。
 *
 * <p>
 * {@code claimableNow} 由服务端按当前客户的已领数量与模板上限给出， 客户端只据此置灰「领取」按钮，不自行推导上限。
 */
@Data
public class MallClaimableCouponVO {

    private Long couponId;

    private String couponName;

    private String discountType;

    private String discountTypeLabel;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal discountValue;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal minOrderAmount;

    private OffsetDateTime validFrom;

    private OffsetDateTime validTo;

    /**
     * 每个客户可领取的上限；{@code null} 表示不限。
     */
    private Integer perCustomerLimit;

    /**
     * 全量领取上限；{@code null} 表示不限。
     */
    private Integer totalLimit;

    /**
     * 当前客户已领取的张数（含已用与已失效）。
     */
    private Integer claimedByCustomer;

    /**
     * 全客户已领取的总张数。
     */
    private Integer claimedTotal;

    /**
     * 当前客户此刻是否还能领取。
     */
    private Boolean claimableNow;
}
