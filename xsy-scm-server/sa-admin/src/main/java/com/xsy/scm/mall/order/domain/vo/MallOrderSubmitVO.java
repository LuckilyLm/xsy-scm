package com.xsy.scm.mall.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 下单结果。订单事实来自正式销售订单，本 VO 只是面向商城的投影。
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
