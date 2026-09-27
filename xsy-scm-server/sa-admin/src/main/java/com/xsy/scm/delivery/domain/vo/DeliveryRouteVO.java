package com.xsy.scm.delivery.domain.vo;

import lombok.Data;

import java.util.List;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.xsy.scm.delivery.domain.entity.DeliveryRouteEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

@Data
public class DeliveryRouteVO extends DeliveryRouteEntity {
    private Integer stopCount;
    private Integer orderCount;
    private Integer locatedCount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal totalAmount;
    /**
     * 发车产生的出库单号；未发车或整条线路零实发（全缺）时为 null。
     */
    private String outboundNo;
}
