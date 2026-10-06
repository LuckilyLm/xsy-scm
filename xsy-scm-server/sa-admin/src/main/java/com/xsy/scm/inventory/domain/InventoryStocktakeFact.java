package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 盘点调整事实。
 *
 * <p>
 * <b>同时带 {@code bookQuantity} 与 {@code actualQuantity}，而不是直接带一个 delta</b>：delta 只有在 **持有余额行锁之后**才能确定作用对象 ——
 * 命令服务要把它施加到确认瞬间的账面量（{@code live}）上， 而不是施加到快照上，因此由命令服务在锁内算 {@code delta = actual - book} 与
 * {@code after = live + delta}。 若调用方直接传 delta，等于把「保存草稿 → 确认」之间的收货 / 出库抹掉。
 *
 * <p>
 * {@code occurredAt} 取**盘点确认时刻**，{@code operator} 取盘点单确认人。
 */
public record InventoryStocktakeFact(Long warehouseId, Long skuId, Long stocktakeId, Long stocktakeItemId,
        BigDecimal bookQuantity, BigDecimal actualQuantity, OffsetDateTime occurredAt, String operator) {
}
