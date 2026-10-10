package com.xsy.scm.mall.catalog.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class MallProductVO {
    private Long skuId;
    private Long spuId;
    private Long categoryId;
    private String productName;
    private String skuCode;
    private String specName;
    private String saleUnit;
    private String productType;
    private String description;
    private String primaryImageKey;
    private String primaryImageUrl;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal unitPrice;
    private String priceStatus;
    private String priceSource;
    private Boolean sellable;
    private String unavailableReason;
    private String stockStatus;
}
