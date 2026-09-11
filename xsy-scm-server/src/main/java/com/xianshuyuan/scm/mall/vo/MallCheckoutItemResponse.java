package com.xianshuyuan.scm.mall.vo;

import com.xianshuyuan.scm.customer.service.PriceSource;
import com.xianshuyuan.scm.product.entity.ProductType;

public record MallCheckoutItemResponse(Long skuId, String productName, String specName, String saleUnit,
                                       ProductType productType, String quantity, String unitPrice,
                                       PriceSource priceSource, String lineAmount) {
}
