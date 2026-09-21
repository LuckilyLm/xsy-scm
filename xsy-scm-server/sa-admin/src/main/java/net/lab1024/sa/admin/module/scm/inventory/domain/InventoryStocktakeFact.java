package net.lab1024.sa.admin.module.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 盘点调整事实（库存域自己的入参记录）。
 *
 * <p>与 {@link InventoryOutboundFact} 同一取向：调用方是库存域自己的盘点单服务，
 * 同域直接传记录即可，不必再包一层跨域契约。
 *
 * <p><b>为什么同时带 {@code bookQuantity} 和 {@code actualQuantity}，而不是直接带一个 delta</b>：
 * delta 只有在**持有余额行锁之后**才能确定它的作用对象 —— 命令服务要把它施加到
 * 确认瞬间的账面量（{@code live}）上，而不是施加到快照上。因此这里传两个真值，
 * 由命令服务在锁内算 {@code delta = actual - book} 与 {@code after = live + delta}。
 * 若调用方直接传 delta，锁内的 live 就失去了意义，等于把「保存草稿 → 确认」之间的
 * 收货/出库抹掉。
 *
 * @param warehouseId     盘点仓库
 * @param skuId           盘点 SKU
 * @param stocktakeId     盘点单 id（头级溯源，不参与防重）
 * @param stocktakeItemId 盘点明细行 id（防重锚点，唯一索引列）
 * @param bookQuantity    账面量快照（保存草稿那一刻），差异基线
 * @param actualQuantity  实盘量（清点结果）
 * @param occurredAt      发生时刻 —— 取**盘点确认时刻**，不是写入时刻
 * @param operator        操作者 —— 取盘点单确认人
 */
public record InventoryStocktakeFact(
        Long warehouseId,
        Long skuId,
        Long stocktakeId,
        Long stocktakeItemId,
        BigDecimal bookQuantity,
        BigDecimal actualQuantity,
        OffsetDateTime occurredAt,
        String operator) {
}
