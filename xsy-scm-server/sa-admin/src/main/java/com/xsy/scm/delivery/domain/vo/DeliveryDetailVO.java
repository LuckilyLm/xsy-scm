package com.xsy.scm.delivery.domain.vo;

import lombok.Data;

import java.util.List;

import com.xsy.scm.delivery.domain.entity.DeliveryRouteOrderEntity;

@Data
public class DeliveryDetailVO {
    private DeliveryRouteVO route;
    private List<
            DeliveryStopVO> stops;
    private List<
            DeliveryRouteOrderEntity> orders;
}
