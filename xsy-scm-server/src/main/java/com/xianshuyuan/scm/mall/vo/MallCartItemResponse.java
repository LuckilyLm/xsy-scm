package com.xianshuyuan.scm.mall.vo;

import com.xianshuyuan.scm.customer.service.PriceSource;
import com.xianshuyuan.scm.product.entity.ProductType;

import java.util.Map;

/**
 * 购物车行。失效商品保留在列表中并给出原因，由客户自行移除，不静默丢弃。
 */
public record MallCartItemResponse(Long skuId, String productName, String specName,
                                   Map<String, String> specValues, String saleUnit, ProductType productType,
                                   String quantity, String unitPrice, PriceSource priceSource, String lineAmount,
                                   boolean available, String reason) {
}
