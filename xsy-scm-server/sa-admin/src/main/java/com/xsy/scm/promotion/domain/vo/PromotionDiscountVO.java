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
 * 三个金额分开返回（基础合计、活动优惠、券优惠）：只给一个「最终优惠」时，
 * 客户问「这张券到底减了多少」无法回答，而对账时需要分别核对。
 */
@Data
public class PromotionDiscountVO {

    private Long salesOrderId;

    private Long customerId;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal baseAmount;

    /** 活动产生的优惠（不含券）。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal activityDiscount;

    /** 券产生的优惠。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal couponDiscount;

    /** 合计优惠（已夹在 [0, 基础合计] 内）。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal discountAmount;

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
}
