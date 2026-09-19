package net.lab1024.sa.admin.module.scm.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 调拨事实（库存域自己的入参记录）。
 *
 * <p>同一个记录被**发出**与**收货**两个动作共用，差别只在 {@code warehouseId} 的含义
 * 与 {@code unitSnapshot} 是否提供：
 *
 * <ul>
 *   <li><b>发出</b>：{@code warehouseId} = 源仓；{@code unitSnapshot} 传 {@code null}
 *       —— 单位由源仓的余额行决定（与销售出库同一取向，见
 *       {@code InventoryCommandService#postTransferOut}）；</li>
 *   <li><b>收货</b>：{@code warehouseId} = 目标仓；{@code unitSnapshot} = 明细行的快照
 *       —— 目标仓若从没有过该 SKU 则用它建立余额行，已有则断言一致（41044）。</li>
 * </ul>
 *
 * @param warehouseId    本次动作作用的仓库（发出 = 源仓，收货 = 目标仓）
 * @param skuId          SKU
 * @param transferId     调拨单 id（头级溯源，不参与防重）
 * @param transferItemId 调拨单行 id（防重锚点之一；另一维是来源类型，见下方说明）
 * @param quantity       调拨数量，必须为正
 * @param unitSnapshot   收货时的期望记账单位；发出时传 {@code null}
 * @param occurredAt     发生时刻 —— 发出取 {@code shippedAt}、收货取 {@code receivedAt}
 * @param operator       操作者 —— 发出取 {@code shippedBy}、收货取 {@code receivedBy}
 */
public record InventoryTransferFact(
        Long warehouseId,
        Long skuId,
        Long transferId,
        Long transferItemId,
        BigDecimal quantity,
        String unitSnapshot,
        OffsetDateTime occurredAt,
        String operator) {
}
