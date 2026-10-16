package com.xsy.scm.customer.domain.vo;

import lombok.Data;

@Data
public class CustomerSkuAliasVO {
    private Long skuId;
    private String skuCode;
    private String productName;
    private String specName;
    private String alias;
    private Integer version;
}
