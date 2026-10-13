package com.xsy.scm.mall.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 结算预览的优惠块：活动与券的试算结果（不占用券，ADR-009）。
 *
 * <p>
 * 金额一律来自营销域试算，客户端只展示；{@code payableAmount} 为优惠后预估应付， 非标品最终仍以实重结算金额为准。
 */
@Data
public class MallCheckoutPromotionVO {

    /** 限时特价让利（按行归集）。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal specialDiscount;

    /** 满减 / 折扣活动让利（不含特价、不含券）。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal activityDiscount;

    /** 所选优惠券让利。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal couponDiscount;

    /** 合计优惠。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal discountAmount;

    /** 优惠后预估应付 = 预估金额 − 合计优惠。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal payableAmount;

    private Long couponInstanceId;

    private String couponName;

    /** 实际生效的活动名，按作用顺序。 */
    private List<String> appliedActivityNames = new ArrayList<>();

    /** 满赠赠品描述（如「土豆 ×2.0000 kg」），非金额权益，不影响应付。 */
    private List<String> giftDescriptions = new ArrayList<>();
}
