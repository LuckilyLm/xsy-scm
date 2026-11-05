package com.xsy.scm.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

/**
 * 应付记录下的收货明细或手工明细。
 */
@Data
public class FinancePayableItemVO {

    private Long payableItemId;

    private String sourceType;

    private Long sourceId;

    private Long purchaseOrderItemId;

    private Long skuId;

    private String skuName;

    private String unit;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal unitPrice;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal amount;
}
