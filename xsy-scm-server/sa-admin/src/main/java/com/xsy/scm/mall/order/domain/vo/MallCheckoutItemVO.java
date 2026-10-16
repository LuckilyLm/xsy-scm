package com.xsy.scm.mall.order.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class MallCheckoutItemVO {
    private Long skuId;
    private String productName;
    private String skuCode;
    private String specName;
    private String saleUnit;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal minOrderQty;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal stepQty;
    private String primaryImageUrl;
    private Boolean nonStandard;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal unitPrice;
    private String priceStatus;
    private String priceSource;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal quantity;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal lineAmount;
    private String availability;
    private String unavailableReason;
}
