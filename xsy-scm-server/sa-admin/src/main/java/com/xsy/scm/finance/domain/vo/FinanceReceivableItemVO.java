package com.xsy.scm.finance.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

/** Frozen sales/outbound/return line details for an AR fact. */
@Data
public class FinanceReceivableItemVO {

    private Long receivableItemId;

    private String sourceType;

    private Long sourceId;

    private Long orderItemId;

    private Long skuId;

    private String skuName;

    private String unit;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal unitPrice;

    /** 行毛额（未扣优惠）。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal grossAmount;

    /** 该行承担的订单优惠分摊（正常为减免额；红字为反向减免额）。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal discountAmount;

    /** 行净额 = 毛额 − 优惠分摊。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal amount;
}
