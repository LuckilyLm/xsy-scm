package com.xsy.scm.promotion.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 优惠试算 / 冻结结果。
 *
 * <p>
 * 三个金额分开返回（基础合计、活动优惠、券优惠）：只给一个「最终优惠」时， 客户问「这张券到底减了多少」无法回答，而对账时需要分别核对。
 */
@Data
public class PromotionDiscountVO {

    private Long salesOrderId;

    private Long customerId;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal baseAmount;

    /** 活动产生的优惠（不含券、不含限时特价）。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal activityDiscount;

    /**
     * 限时特价让利（不含满减/折扣与券）。
     *
     * <p>
     * 与 {@link #activityDiscount} 分开：特价作用在<b>基础价之上、其余活动之前</b>，且让利按<b>行</b>归集 （针对某个
     * SKU），不是按订单金额比例分摊的订单级优惠。分开才能回答「原基础价多少、特价让了多少」。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal specialDiscount;

    /** 券产生的优惠。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal couponDiscount;

    /** 合计优惠（已夹在 [0, 基础合计] 内）。 */
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

    /** 试算过程中被互斥组挤掉的活动（可解释：为什么另一条没生效）。 */
    private List<String> suppressedActivities = new ArrayList<>();

    /**
     * 实际产生优惠的<b>全部</b>活动，按作用顺序。
     *
     * <p>
     * 与 {@link #activityId} 的区别：{@code activityId} 只记第一条产生优惠的活动（主规则，便于快速展示）， 不同互斥组可以叠加，因此真实生效的可能不止一条。冻结时必须按本列表完整落快照，
     * 否则退款反向会漏掉叠加的那部分。
     */
    private List<AppliedActivityVO> appliedActivities = new ArrayList<>();

    /**
     * 满赠赠品权益：<b>非金额权益</b>，不参与优惠分摊、不进 {@link #discountAmount}， 而是单独冻结成 {@code order_promotion_gift}，供出库、分拣、小票与成本归集读取。
     */
    private List<GiftEntitlementVO> gifts = new ArrayList<>();

    /** 本次结果是否已冻结（确认下单为 true，试算为 false）。 */
    private boolean frozen;

    private OffsetDateTime createdAt;

    private String createdBy;

    /** 活动规则原文（受控键值），便于界面解释「按什么规则算的」。 */
    private Map<String, Object> activityRule;

    @Data
    public static class PromotionDiscountAllocationVO {

        private Long orderItemId;

        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal baseAmount;

        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal discountAmount;
    }

    /** 一条实际生效的活动：规则与它这一次贡献的优惠额一并留下，供退款反向与解释使用。 */
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

    /** 一条赠品权益：活动、赠品 SKU 快照与数量，冻结后不可改。 */
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

        /** 活动规则原文（受控键值），供解释「满多少赠多少」。 */
        private Map<String, Object> rule;
    }
}
