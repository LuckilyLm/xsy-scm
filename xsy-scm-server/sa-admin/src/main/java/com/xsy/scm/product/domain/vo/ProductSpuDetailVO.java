package com.xsy.scm.product.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.math.BigDecimal;
import java.time.OffsetDateTime;


@Data
@EqualsAndHashCode(callSuper = true)
public class ProductSpuDetailVO extends ProductSpuVO {
    private List<ProductImageVO> images;
    private OffsetDateTime createdAt;
    private Integer shelfLifeDays;
    private BigDecimal lossRate;
    private Integer purchaseWarningDays;
    private String invoiceName;
    private String taxCategoryCode;
    private Boolean taxExempt;
    private BigDecimal taxRate;
}
