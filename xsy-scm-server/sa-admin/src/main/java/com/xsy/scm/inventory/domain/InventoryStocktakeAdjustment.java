package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;

/**
 * 一条盘点明细行的调整结果（{@code InventoryCommandService} 的返回值）。
 *
 * <p>
 * {@code movementWritten} 是关键字段：{@code quantity} 恒为正，差异为 0 的行写不出「零差异」流水， 调用方必须能区分「调整了 0」和「压根不需要调整」，否则操作日志会谎报盘点条数。
 */
public record InventoryStocktakeAdjustment(String unit, BigDecimal delta, BigDecimal beforeQuantity,
        BigDecimal afterQuantity, boolean movementWritten) {
}
