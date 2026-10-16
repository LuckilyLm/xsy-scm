package com.xsy.scm.supplier.domain.vo;

import lombok.Data;

import java.util.Map;

/** 保存供应商可供商品的只读信息。商品名来自 SPU，规格名来自 SKU；缺失的规格属性按空对象处理。 */
@Data
public class OrderableSkuVO {

    private Long skuId;

    private String skuCode;

    private String productName;

    private Map<String, String> specValues;
}
