package com.xsy.scm.product.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class ProductBundleComponentVO {
    private Long componentSkuId;
    private String skuCode;
    private String productName;
    private String specName;
    private String saleUnit;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal quantityPerBundleSaleUnit;
}
