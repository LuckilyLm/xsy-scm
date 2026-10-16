package com.xsy.scm.metrics.domain;

/** 库存健康度按正常、低库存、高库存、未配置和缺货分组，各组互斥。缺货优先判断，不受预警阈值影响。 */
public record InventoryHealth(long totalSkuCount, long normalCount, long lowCount, long highCount,
        long unconfiguredCount, long outOfStockCount) {
}
