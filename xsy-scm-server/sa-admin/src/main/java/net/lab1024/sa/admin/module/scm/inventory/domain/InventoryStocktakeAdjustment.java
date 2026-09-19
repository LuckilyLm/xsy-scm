package net.lab1024.sa.admin.module.scm.inventory.domain;

import java.math.BigDecimal;

/**
 * 一条盘点明细行的调整结果（{@code InventoryCommandService} 的返回值）。
 *
 * <p>为什么要返回这么多字段而不是只返回记账单位：调用方（盘点单服务）需要把
 * {@code unitSnapshot} 回写到明细行，并需要一个可用于操作日志的摘要。
 * 更关键的是 {@code movementWritten} —— **差异为 0 的行不写流水**
 * （{@code quantity} 恒为正，写不出「零差异」流水），调用方必须能区分
 * 「调整了 0」和「压根不需要调整」，否则日志会谎报盘点条数。
 *
 * @param unit           记账单位（= 余额行的 unit，Q13）
 * @param delta          差异 = 实盘量 − 账面量快照；正为盘盈、负为盘亏、0 为账实相符
 * @param beforeQuantity 确认瞬间持锁读到的账面量（流水的 before）
 * @param afterQuantity  调整后的账面量 = before + delta（流水的 after）
 * @param movementWritten 本次是否真的写了流水（{@code delta == 0} 时为 {@code false}）
 */
public record InventoryStocktakeAdjustment(
        String unit,
        BigDecimal delta,
        BigDecimal beforeQuantity,
        BigDecimal afterQuantity,
        boolean movementWritten) {
}
