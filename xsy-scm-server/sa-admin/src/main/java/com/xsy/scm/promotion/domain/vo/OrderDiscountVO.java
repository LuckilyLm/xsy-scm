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
 * 订单已冻结优惠的读模型。
 *
 * <p>
 * 只读：优惠一旦冻结就是不可变快照（表上有触发器），因此这里没有写入形态，也不提供「改优惠」的入口。
 * 订单详情嵌这个对象回答「这单当时按什么规则减了多少、用了哪张券」；退款反向与对账都从同一份快照读，
 * 不会按当前活动重算。
 */
@Data
public class OrderDiscountVO {

    private Long salesOrderId;

    /** 主活动（第一条产生优惠的活动）；叠加生效的其余活动在 {@link #activitySnapshot} 的 applied 里。 */
    private Long activityId;

    private Integer activityVersion;

    /**
     * 活动快照：{@code {"applied":[{activityId,activityCode,activityName,activityType,version,rule,discountAmount}...],
     * "suppressed":[...]}}。原样返回冻结时的结构，不在读路径上重算。
     */
    private Map<String, Object> activitySnapshot;

    private Long couponInstanceId;

    /** 券快照：券编码 / 名称 / 券优惠额。 */
    private Map<String, Object> couponSnapshot;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal baseAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal discountAmount;

    private List<Allocation> allocations = new ArrayList<>();

    /** 承接舍入差额的行；没有差额时为 {@code null}。 */
    private Long roundingTargetItemId;

    private OffsetDateTime createdAt;

    private String createdBy;

    /** 逐行分摊：退款按这份分摊反向，不用退款时的当前活动重算。 */
    @Data
    public static class Allocation {

        private Long orderItemId;

        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal baseAmount;

        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal discountAmount;
    }
}
