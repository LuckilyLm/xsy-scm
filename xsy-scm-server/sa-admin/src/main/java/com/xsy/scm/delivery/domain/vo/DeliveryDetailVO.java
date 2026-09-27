package com.xsy.scm.delivery.domain.vo;

import lombok.Data;

import java.util.List;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.xsy.scm.delivery.domain.entity.DeliveryRouteOrderEntity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

@Data
public class DeliveryDetailVO {
    private DeliveryRouteVO route;
    private List<DeliveryStopVO> stops;
    private List<DeliveryRouteOrderEntity> orders;
}
