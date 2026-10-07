package com.xsy.scm.metrics.domain;

/**
 * 主档计数。
 *
 * <p>
 * 客户按客户归属维度收窄；供应商与商品规格没有仓库列也没有归属列（采购归属在 {@code supplier_sku} 上），按裁决属团队共享读，不收窄 —— 这不是漏判。
 */
public record MasterDataMetrics(long customerCount, long supplierCount, long skuCount) {
}
