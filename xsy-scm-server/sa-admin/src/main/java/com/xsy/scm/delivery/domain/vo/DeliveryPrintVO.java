package com.xsy.scm.delivery.domain.vo;

import lombok.Data;

import java.util.List;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.xsy.scm.delivery.domain.entity.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

@Data
public class DeliveryPrintVO {
    private DeliveryDetailVO detail;
    private List<DeliveryPrintItemVO> items;
}
