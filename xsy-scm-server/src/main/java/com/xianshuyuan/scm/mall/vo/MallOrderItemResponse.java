package com.xianshuyuan.scm.mall.vo;

import com.xianshuyuan.scm.order.entity.PriceSource;
import com.xianshuyuan.scm.product.entity.ProductType;

import java.util.Map;

/**
 * 订单行。同时给出订购数量与已确认实重（非标品实重依赖后台分拣确认）。
 */
public record MallOrderItemResponse(Long id, Long skuId, String productName, String specName,
                                    Map<String, String> specValues, String saleUnit, ProductType productType,
                                    String orderedQuantity, String actualQuantity, String unitPrice,
                                    PriceSource priceSource, String amount) {
}
