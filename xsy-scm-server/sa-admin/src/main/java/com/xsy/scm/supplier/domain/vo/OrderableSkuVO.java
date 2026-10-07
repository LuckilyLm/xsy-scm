package com.xsy.scm.supplier.domain.vo;

import lombok.Data;

import java.util.Map;

/**
 * 「可下单 SKU」投影：用于构造 {@code supplier_sku} 的快照。
 *
 * <p>
 * 名称取 SPU 的名称（{@code productName}）而不是 SKU 的 {@code specName}；{@code specValues} 为 {@code null} 时按空对象落库。
 *
 * <p>
 * 对应的只读查询由 supplier 域自己持有：只读 {@code product_sku} / {@code product_spu} 是允许的，
 * 但向 product 域加方法会建立 supplier → product 的依赖，跨域读不这样表达。
 */
@Data
public class OrderableSkuVO {

    private Long skuId;

    private String skuCode;

    private String productName;

    private Map<String, String> specValues;
}
