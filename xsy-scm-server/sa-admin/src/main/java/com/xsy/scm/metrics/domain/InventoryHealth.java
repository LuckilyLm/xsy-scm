package com.xsy.scm.metrics.domain;

/**
 * 库存健康度分档。
 *
 * <p>
 * 五档互斥，{@code normal + low + high + unconfigured + outOfStock} 恒等于 {@code totalSkuCount}（参与评估的「仓库 × 商品规格」数）。
 * 缺货单独分档且优先于其它档，不依赖阈值配置 —— 否则「配了阈值但一件都没有」会被算成预警，缺货段恒为 0。
 */
public record InventoryHealth(long totalSkuCount, long normalCount, long lowCount, long highCount,
        long unconfiguredCount, long outOfStockCount) {
}
