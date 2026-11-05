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
 * 订单优惠的读模型。
 *
 * <p>
 * 只读：优惠一旦保存就不可改（表上有触发器），没有写入形态，也没有「改优惠」的入口。 订单详情用它回答「当时按什么规则减了多少、用了哪张券」；退款反向和对账都读这份数据，不按当前活动重算。
 */
@Data
public class OrderDiscountVO {

    private Long salesOrderId;

    /** 主活动（第一条产生优惠的活动）；叠加生效的其余活动在 {@link #activitySnapshot} 的 applied 里。 */
    private Long activityId;

    private Integer activityVersion;

    /**
     * 当时的活动内容：{@code {"applied":[{activityId,activityCode,activityName,activityType,version,rule,discountAmount}...],
     * "suppressed":[...]}}。原样返回保存时的结构，读取时不重算。
     */
    private Map<String, Object> activitySnapshot;

    private Long couponInstanceId;

    /** 当时的券内容：券编码 / 名称 / 券优惠额。 */
    private Map<String, Object> couponSnapshot;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal baseAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal discountAmount;

    /**
     * 其中的限时特价让利额。
     *
     * <p>
     * 与 {@link #discountAmount} 是「其中」的关系（表上有 CHECK 保证不大于合计）；单独给出才能回答「原基础价多少、特价让了多少、最后多少」。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal specialDiscountAmount;

    private List<Allocation> allocations = new ArrayList<>();

    /** 承接舍入差额的行；没有差额时为 {@code null}。 */
    private Long roundingTargetItemId;

    private OffsetDateTime createdAt;

    @JsonSerialize(using = ScmOperatorNameSerializer.class)
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
