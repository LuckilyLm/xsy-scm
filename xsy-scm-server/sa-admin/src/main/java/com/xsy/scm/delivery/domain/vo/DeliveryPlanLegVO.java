package com.xsy.scm.delivery.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 排线建议里的一段（一个停靠点）。
 *
 * <p>
 * 每段都带本段距离和累计距离：只有总距离，就看不出这单为什么排在前面，没法解释的建议没人敢用。
 */
@Data
public class DeliveryPlanLegVO {

    /** 访问顺序，从 1 开始；1 是第一个停靠点（起点不计入）。 */
    private Integer seq;

    /** 建议生成时该站在原线路中的顺序，用于人工比对，不回读已修改的线路。 */
    private Integer originalSeq;

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
