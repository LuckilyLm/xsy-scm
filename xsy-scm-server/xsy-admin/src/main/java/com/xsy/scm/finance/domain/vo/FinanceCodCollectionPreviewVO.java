package com.xsy.scm.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

import lombok.Data;

/**
 * COD 收款前展示订单归属和当前可核销余额；提交命令会重新校验。
 */
@Data
public class FinanceCodCollectionPreviewVO {
    private Long orderId;
    private String orderNo;
    private Long customerId;
    private String customerName;
    private String settlementCustomerName;
    private String receivableNo;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal netAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal openAmount;
}
