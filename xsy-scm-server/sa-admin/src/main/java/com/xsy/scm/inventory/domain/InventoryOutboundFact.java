package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 出库事实。
 *
 * <p>
 * 不像入库那样走「跨域契约」：入库的调用方是采购域，需要中立契约解耦；出库由库存域自己的出库单服务调用，同域直接传记录即可。未来若销售发货要触发出库，再补一个对外契约，由契约适配成本记录。
 *
 * <p>
 * 数量恒为正，方向由 {@code movementType} 表达；{@code occurredAt} 取<b>出库确认时刻</b>， {@code operator} 取出库单确认人。
 */
public record InventoryOutboundFact(Long warehouseId, Long skuId, Long outboundId, Long outboundItemId,
        BigDecimal quantity, BigDecimal unitCost, OffsetDateTime occurredAt, String operator) {
}
