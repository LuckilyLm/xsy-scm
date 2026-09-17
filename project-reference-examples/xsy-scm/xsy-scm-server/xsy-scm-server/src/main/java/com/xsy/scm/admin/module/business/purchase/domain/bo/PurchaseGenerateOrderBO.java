package com.xsy.scm.admin.module.business.purchase.domain.bo;

import com.xsy.scm.admin.module.business.purchase.domain.entity.PurchaseItemEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购单生成分组（一个供应商一张采购单）
 */
@Data
public class PurchaseGenerateOrderBO {

    /**
     * 供应商 ID
     */
    private Long supplierId;

    /**
     * 供应商名称
     */
    private String supplierName;

    /**
     * 采购预估金额（不含税）
     */
    private BigDecimal totalAmount;

    /**
     * 采购明细
     */
    private List<PurchaseItemEntity> items;
}
