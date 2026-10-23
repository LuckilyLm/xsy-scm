package com.xsy.scm.customer.domain.vo;

import lombok.Data;

/** 客户可见性列表的客户卡片：一家客户一条，带可见商品与规格数。 */
@Data
public class CustomerSkuVisibilitySummaryVO {
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String customerTypeName;
    private String visibilityPolicy;
    private Long productCount;
    private Long specCount;
}
