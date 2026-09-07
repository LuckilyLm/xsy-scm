package com.xianshuyuan.scm.order.vo;

import com.xianshuyuan.scm.order.entity.*;
import com.xianshuyuan.scm.product.entity.ProductType;

import java.util.Map;

public record SalesOrderItemResponse(Long id, Integer version, Long skuId, Long productId, String skuCode,
                                     String productName, String specName, Map<String, String> specValues,
                                     String saleUnit, ProductType productType, String orderedQuantity,
                                     String actualQuantity, String unitPrice, String lockedUnitPrice,
                                     PriceSource priceSource, Long priceSourceId, String amount,
                                     String overrideReason) {
}
