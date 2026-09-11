package com.xianshuyuan.scm.marketing.row;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 常用菜品查询行。联表带出商品名称、规格、销售单位与建议单价，供"常用菜品"与"再来一单"使用。
 */
@Data
public class FrequentSkuRow {

    private Long id;
    private Long customerId;
    private Long skuId;
    private String productName;
    private String skuCode;
    private String specName;
    private Map<String, String> specValues;
    private String saleUnit;
    private BigDecimal marketPrice;
    private BigDecimal lastUnitPrice;
    private BigDecimal lastQuantity;
    private Integer buyCount;
    private Long lastOrderId;
    private OffsetDateTime lastOrderedAt;
}
