package com.xsy.scm.promotion.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.promotion.domain.entity.PromotionCouponEntity;
import com.xsy.scm.promotion.domain.entity.PromotionCouponInstanceEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class PromotionCouponVO {

    private Long id;

    private String couponCode;

    private String couponName;

    private String discountType;

    private String discountTypeLabel;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal discountValue;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal minOrderAmount;

    private OffsetDateTime validFrom;

    private OffsetDateTime validTo;

    private String status;
    private Boolean claimable;
    private Integer perCustomerLimit;
    private Integer totalLimit;

    private String remark;

    private Integer version;

    private UsageStats usageStats;

    @Data
    public static class UsageStats {
        private Long couponId;
        private Long adminIssuedCount;
        private Long mallClaimedCount;
        private Long usedCount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal usedDiscountAmount;
        private Long discountMissingCount;
    }

    public static PromotionCouponVO of(PromotionCouponEntity row, String typeLabel) {
        PromotionCouponVO vo = new PromotionCouponVO();
        vo.setId(row.getId());
        vo.setCouponCode(row.getCouponCode());
        vo.setCouponName(row.getCouponName());
        vo.setDiscountType(row.getDiscountType());
        vo.setDiscountTypeLabel(typeLabel);
        vo.setDiscountValue(row.getDiscountValue());
        vo.setMinOrderAmount(row.getMinOrderAmount());
        vo.setValidFrom(row.getValidFrom());
        vo.setValidTo(row.getValidTo());
        vo.setStatus(row.getStatus());
        vo.setClaimable(row.getClaimable());
        vo.setPerCustomerLimit(row.getPerCustomerLimit());
        vo.setTotalLimit(row.getTotalLimit());
        vo.setRemark(row.getRemark());
        vo.setVersion(row.getVersion());
        return vo;
    }

    /** 客户券实例（列表用）。 */
    @Data
    public static class Instance {

        private Long id;

        private Long couponId;

        private String couponCode;

        private String couponName;

        private Long customerId;

        private String instanceNo;

        private String status;

        private Long reservedOrderId;

        private OffsetDateTime reservedAt;

        private Long usedOrderId;

        private OffsetDateTime usedAt;

        private OffsetDateTime releasedAt;

        private String releaseReason;

        private Integer version;

        public static Instance of(PromotionCouponInstanceEntity row, PromotionCouponEntity coupon) {
            Instance vo = new Instance();
            vo.setId(row.getId());
            vo.setCouponId(row.getCouponId());
            vo.setCouponCode(coupon == null ? null : coupon.getCouponCode());
            vo.setCouponName(coupon == null ? null : coupon.getCouponName());
            vo.setCustomerId(row.getCustomerId());
            vo.setInstanceNo(row.getInstanceNo());
            vo.setStatus(row.getStatus());
            vo.setReservedOrderId(row.getReservedOrderId());
            vo.setReservedAt(row.getReservedAt());
            vo.setUsedOrderId(row.getUsedOrderId());
            vo.setUsedAt(row.getUsedAt());
            vo.setReleasedAt(row.getReleasedAt());
            vo.setReleaseReason(row.getReleaseReason());
            vo.setVersion(row.getVersion());
            return vo;
        }
    }
}
