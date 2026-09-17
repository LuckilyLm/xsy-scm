package com.xsy.scm.admin.module.business.purchase.domain.bo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品库存余额聚合
 */
@Data
public class ProductStockAggBO {

    /**
     * 商品 ID
     */
    private Long productId;

    /**
     * 现有库存数量
     */
    private BigDecimal stockQuantity;
}
