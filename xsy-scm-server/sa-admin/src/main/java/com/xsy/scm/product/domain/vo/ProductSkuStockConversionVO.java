package com.xsy.scm.product.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class ProductSkuStockConversionVO {
    private String stockUnit;
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal stockUnitsPerSaleUnit;
}
