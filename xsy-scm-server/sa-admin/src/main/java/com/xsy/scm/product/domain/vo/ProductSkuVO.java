package com.xsy.scm.product.domain.vo;

import lombok.Data;

import java.util.Map;
import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

@Data
public class ProductSkuVO {
    private Long skuId;
    private Integer version;
    private String skuCode;
    private String barcode;
    private String specName;
    private Map<String, String> specValues;
    private String saleUnit;
    private String productType;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal marketPrice;
    private String status;
    private Boolean defaultFlag;
    private Integer sortOrder;
}
