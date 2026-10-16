package com.xsy.scm.mall.catalog.domain.vo;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class MallStockFact {
    private Long skuId;
    private String saleUnit;
    private String stockUnit;
    private BigDecimal availableStockQuantity;
    private BigDecimal stockUnitsPerSaleUnit;
}
