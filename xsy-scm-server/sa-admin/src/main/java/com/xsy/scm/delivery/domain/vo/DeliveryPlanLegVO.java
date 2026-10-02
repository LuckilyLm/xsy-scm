package com.xsy.scm.delivery.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 排线建议里的一段（一个停靠点）。
 *
 * <p>
 * 每段都带**本段距离**与**累计距离**：只有总距离时，操作者无法回答「为什么这单排在前面」，
 * 而「可解释」正是这条建议能被采纳的前提。
 */
@Data
public class DeliveryPlanLegVO {

    /**
     * 访问顺序，从 1 开始；1 是第一个停靠点（起点不计入）。
     */
    private Integer seq;

    private Long stopId;

    private String customerNameSnapshot;

    private String addressSnapshot;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal legDistance;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal cumulativeDistance;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal longitude;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal latitude;
}
