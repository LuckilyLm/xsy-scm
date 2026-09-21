package net.lab1024.sa.admin.module.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 出库事实（库存域自己的入参记录）。
 *
 * <p>为什么不像入库那样走「跨域契约」：入库的调用方是采购域，需要一个中立的契约来解耦
 * （见 {@code PurchaseInventoryContract}）；而本波次的出库调用方是**库存域自己的出库单服务**，
 * 同域直接传记录即可，再包一层契约只会多一层没有解耦价值的间接。
 * 未来若销售发货要触发出库，再补一个对外契约，由契约适配成本记录。
 *
 * @param warehouseId    出库仓库
 * @param skuId          出库 SKU
 * @param outboundId     出库单 id（头级溯源，不参与防重）
 * @param outboundItemId 出库单行 id（防重锚点，唯一索引列）
 * @param quantity       出库数量，必须为正（方向由 movementType 表达，数量恒正）
 * @param unitCost       成本快照；出库暂无成本核算，允许为空
 * @param occurredAt     发生时刻 —— 取**出库确认时刻**，不是写入时刻
 * @param operator       操作者 —— 取出库单确认人
 */
public record InventoryOutboundFact(
        Long warehouseId,
        Long skuId,
        Long outboundId,
        Long outboundItemId,
        BigDecimal quantity,
        BigDecimal unitCost,
        OffsetDateTime occurredAt,
        String operator) {
}
