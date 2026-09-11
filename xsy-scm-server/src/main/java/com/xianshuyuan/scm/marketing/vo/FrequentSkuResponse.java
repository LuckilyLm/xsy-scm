package com.xianshuyuan.scm.marketing.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 常用菜品视图。lastUnitPrice / lastQuantity 取自最近一次订单明细，便于一键加购。
 */
public record FrequentSkuResponse(Long skuId, String productName, String skuCode, String specName,
                                  Map<String, String> specValues, String saleUnit, BigDecimal marketPrice,
                                  BigDecimal lastUnitPrice, BigDecimal lastQuantity, Integer buyCount,
                                  Long lastOrderId, OffsetDateTime lastOrderedAt) {
}
