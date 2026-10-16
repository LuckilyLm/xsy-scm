package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 盘点事实同时保存账面量与实盘量。只有锁住库存余额后才能计算实际差异并记流水，不能信任草稿阶段的差异量。 */
public record InventoryStocktakeFact(Long warehouseId, Long skuId, Long stocktakeId, Long stocktakeItemId,
        BigDecimal bookQuantity, BigDecimal actualQuantity, OffsetDateTime occurredAt, String operator) {
}
