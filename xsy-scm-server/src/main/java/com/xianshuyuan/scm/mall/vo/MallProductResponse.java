package com.xianshuyuan.scm.mall.vo;

import com.xianshuyuan.scm.customer.service.PriceSource;
import com.xianshuyuan.scm.product.entity.ProductType;

import java.util.Map;

/**
 * 商城商品。价格取自服务端价格解析结果，客户端不得自行计算或改价。
 */
public record MallProductResponse(Long skuId, Long spuId, String productName, String skuCode, String specName,
                                  Map<String, String> specValues, String saleUnit, ProductType productType,
                                  Long categoryId, String categoryName, String marketPrice, String unitPrice,
                                  PriceSource priceSource) {
}
