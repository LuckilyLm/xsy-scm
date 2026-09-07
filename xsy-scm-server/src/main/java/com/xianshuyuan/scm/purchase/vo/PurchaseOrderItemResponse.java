package com.xianshuyuan.scm.purchase.vo;
import com.xianshuyuan.scm.product.entity.ProductType; import java.math.BigDecimal; import java.util.Map;
public record PurchaseOrderItemResponse(Long id, Integer version, Long spuId, Long skuId, String spuCode, String productName, String skuCode, String skuName, Map<String,String> specValues, String purchaseUnit, ProductType productType, BigDecimal plannedQuantity, BigDecimal receivedQuantity, BigDecimal purchasePrice, BigDecimal lineAmount) {}
