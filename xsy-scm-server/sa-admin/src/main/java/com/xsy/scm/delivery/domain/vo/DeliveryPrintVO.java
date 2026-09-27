package com.xsy.scm.delivery.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class DeliveryPrintVO {
    private DeliveryDetailVO detail;
    private List<DeliveryPrintItemVO> items;
}
