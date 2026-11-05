package com.xsy.scm.delivery.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

import com.xsy.scm.delivery.domain.entity.DeliveryRouteStopEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

@Data
public class DeliveryStopVO extends DeliveryRouteStopEntity {
    private Integer orderCount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal totalAmount;
}
