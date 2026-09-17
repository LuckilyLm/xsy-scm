package com.xsy.scm.admin.module.business.purchase.domain.bo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品供应商聚合（含供应价）
 */
@Data
public class ProductSupplierAggBO {

    /**
     * 商品 ID
     */
    private Long productId;

    /**
     * 供应商 ID
     */
    private Long supplierId;

    /**
     * 供应商名称
     */
    private String supplierName;

    /**
     * 供应价（不含税）
     */
    private BigDecimal supplyPrice;

    /**
     * 是否默认供应商
     */
    private Boolean defaultFlag;
}
