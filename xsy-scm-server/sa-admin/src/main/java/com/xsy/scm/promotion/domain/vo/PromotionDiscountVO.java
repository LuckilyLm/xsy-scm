package com.xsy.scm.promotion.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 优惠试算 / 保存后的结果。
 *
 * <p>
 * 基础合计、活动优惠、券优惠分开返回：能回答「这张券到底减了多少」，对账也能分别核对。
 */
@Data
public class PromotionDiscountVO {

    private Long salesOrderId;

    private Long customerId;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal baseAmount;

    /** 活动产生的优惠，不含券和限时特价。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal activityDiscount;

    /** 限时特价让利，归属对应商品行；其余满减与券优惠再按订单规则分摊。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal specialDiscount;

    /** 券产生的优惠。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal couponDiscount;

    /** 合计优惠，已限制在 [0, 基础合计] 内。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal discountAmount;

    /** 客户实付 = 基础合计 − 合计优惠。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal finalAmount;

    private Long activityId;

    private String activityCode;

    private String activityName;

    private Integer activityVersion;

    private Long couponInstanceId;

    private String couponCode;

    private String couponName;

    private List<PromotionDiscountAllocationVO> allocations = new ArrayList<>();

    /** 承接舍入差额的行；没有差额时为 {@code null}。 */
    private Long roundingTargetItemId;

    /** 被互斥组挤掉的活动，用于解释为什么另一条没生效。 */
    private List<String> suppressedActivities = new ArrayList<>();

    /** 全部生效活动：用于保存和退款；主活动 ID 只用于快速展示。 */
    private List<AppliedActivityVO> appliedActivities = new ArrayList<>();

    /**
     * 满赠赠品：不参与优惠分摊，不进 {@link #discountAmount}，单独保存到 {@code xsy_order_promotion_gift}，供出库、分拣、小票和成本核算使用。
     */
    private List<GiftEntitlementVO> gifts = new ArrayList<>();

    /** 本次结果是否已保存（确认下单为 true，试算为 false）。 */
    private boolean frozen;

    private OffsetDateTime createdAt;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;

    /** 活动规则原文（受控键值），用于界面解释「按什么规则算的」。 */
    private Map<String, Object> activityRule;

    @Data
    public static class PromotionDiscountAllocationVO {

        private Long orderItemId;

        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal baseAmount;

        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal discountAmount;
    }

    /** 一条实际生效的活动：规则与这次贡献的优惠额一并留下，供退款反向与解释使用。 */
    @Data
    public static class AppliedActivityVO {

        private Long activityId;

        private String activityCode;

        private String activityName;

        private String activityType;

        private Integer version;

        /** 活动规则原文（受控键值）。 */
        private Map<String, Object> rule;

        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal discountAmount;
    }

    /** 一条赠品权益：活动、赠品 SKU 信息与数量，保存后不可改。 */
    @Data
    public static class GiftEntitlementVO {

        private Long activityId;

        private String activityCode;

        private String activityName;

        private Integer version;

        private Long skuId;

        private String skuCode;

        private String productName;

        private String specName;

        private String saleUnit;

        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;

        /** 活动规则原文（受控键值），用于解释「满多少赠多少」。 */
        private Map<String, Object> rule;
    }
}
