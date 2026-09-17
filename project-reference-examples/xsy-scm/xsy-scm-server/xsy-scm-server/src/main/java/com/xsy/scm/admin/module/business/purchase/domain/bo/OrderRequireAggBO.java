package com.xsy.scm.admin.module.business.purchase.domain.bo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单汇总需求聚合（商品 + 规格维度）
 */
@Data
public class OrderRequireAggBO {

    /**
     * 商品 ID
     */
    private Long productId;

    /**
     * 规格 ID
     */
    private Long skuId;

    /**
     * 商品名称
     */
    private String productName;

    /**
     * 订单汇总需求量
     */
    private BigDecimal requireQuantity;
}
