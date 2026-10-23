package com.xsy.scm.mall.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 下单结果。订单数据来自正式销售订单，本 VO 只是面向商城的展示视图。
 */
@Data
public class MallOrderSubmitVO {
    private Long orderId;
    private String orderNo;
    private String status;
    private String orderSource;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal orderedTotalAmount;
    private OffsetDateTime createdAt;
}
