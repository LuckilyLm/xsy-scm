package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 出库事实。
 *
 * <p>
 * 不像入库那样走跨域接口：入库的调用方是采购域，需要中立接口解耦；出库由库存域自己的出库单服务调用，同域直接传记录即可。 未来若销售发货要触发出库，再补一个对外接口，由接口适配成本记录。
 *
 * <p>
 * 数量恒为正，方向由 {@code movementType} 表达；{@code occurredAt} 取出库确认时刻，{@code operator} 取出库单确认人。
 */
public record InventoryOutboundFact(Long warehouseId, Long skuId, Long outboundId, Long outboundItemId,
    BigDecimal quantity, BigDecimal unitCost, OffsetDateTime occurredAt, String operator) {
}
