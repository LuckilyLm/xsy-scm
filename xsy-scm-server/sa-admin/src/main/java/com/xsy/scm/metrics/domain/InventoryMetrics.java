package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;

/**
 * 库存指标。
 *
 * <p>
 * 数量口径是<b>余额表的 quantity 之和</b>（不是流水净额），因此与趋势里的 {@code inventoryQuantity}（期末累计净额）在历史日期上会不同 —— 前者是「此刻账面」，后者是「当日及之前的流水累计」。
 */
public record InventoryMetrics(BigDecimal totalQuantity, long skuCount, long warehouseCount, long todayInboundCount,
        long todayOutboundCount, InventoryHealth health) {
}
