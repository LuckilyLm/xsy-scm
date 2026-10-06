package com.xsy.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 调拨事实。发出与收货共用本记录，差别只在 {@code warehouseId} 的含义与 {@code unitSnapshot}：
 *
 * <ul>
 * <li><b>发出</b>：{@code warehouseId} = 源仓；{@code unitSnapshot} 传 {@code null} —— 单位由源仓的余额行决定 （与销售出库同一取向）；</li>
 * <li><b>收货</b>：{@code warehouseId} = 目标仓；{@code unitSnapshot} = 明细行的快照 —— 目标仓若从没有过该 SKU 则用它建立余额行，已有则断言一致（41044）。</li>
 * </ul>
 *
 * <p>
 * {@code occurredAt} / {@code operator} 发出取 {@code shippedAt} / {@code shippedBy}， 收货取 {@code receivedAt} /
 * {@code receivedBy}。
 */
public record InventoryTransferFact(Long warehouseId, Long skuId, Long transferId, Long transferItemId,
        BigDecimal quantity, String unitSnapshot, OffsetDateTime occurredAt, String operator) {
}
