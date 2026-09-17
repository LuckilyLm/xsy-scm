package net.lab1024.sa.admin.module.scm.purchase.support;

import java.math.BigDecimal;

/**
 * Future integration only: W5 neither implements nor calls inventory mutations.
 *
 * <p>W6 (Inventory) is expected to implement this contract. The single call site will be
 * {@code PurchaseReceiptService.confirm(...)}, inside the same transaction, immediately after
 * the purchase-side writes. W5 ships {@link NoOpPurchaseInventoryContract} and never invokes
 * any method, so W5 收货确认 does not create inventory facts.
 *
 * <p>设计依据：W5 Target Design §8.2（契约定义）+ §8.5（Q5 修订后的 W6 bootstrap 口径）。
 */
public interface PurchaseInventoryContract {

    /**
     * 稳定唯一源键的文档类型常量（Q5）。
     *
     * <p>W6 的库存流水必须持久化 {@code source_document_type = SOURCE_DOCUMENT_TYPE} 与
     * {@code source_document_item_id = InboundFact.receiptItemId}，并据此建立部分唯一索引
     * {@code uk (source_document_type, source_document_item_id) WHERE deleted = FALSE}，
     * 使「历史 backfill / 未来实时 confirm / 重试」三者不可重复入库。
     */
    String SOURCE_DOCUMENT_TYPE = "PURCHASE_RECEIPT_ITEM";

    /**
     * 一次「已确认的收货行」所代表的入库事实。
     *
     * <p>W5 只负责产生这个事实；W6 决定它是被 push（本方法）还是 pull（W6 读表）。
     * {@code idempotencyKey} 由 W6 用于防重（W6 侧应有
     * {@code uk (source_document_type, source_document_item_id)} 的部分唯一索引）。
     *
     * @param purchaseOrderId 采购单 id
     * @param receiptId       收货单 id
     * @param receiptItemId   稳定唯一源键的 item 维度：{@code source_document_item_id}（Q5）
     * @param warehouseId     仓库 id（采购单绑定，收货单继承）
     * @param skuId           SKU id
     * @param warehouseCode   仓库编码快照
     * @param warehouseName   仓库名称快照
     * @param skuCode         SKU 编码快照
     * @param skuName         SKU 名称快照
     * @param unit            采购单位快照（{@code purchase_unit_snapshot}）
     * @param quantity        有效数量（{@code received_quantity}）
     * @param unitCost        采购单价快照（来自 {@code purchase_order_item.purchase_price}）
     * @param idempotencyKey  W6 侧防重键
     */
    record InboundFact(
            Long purchaseOrderId,
            Long receiptId,
            Long receiptItemId,
            Long warehouseId,
            Long skuId,
            String warehouseCode,
            String warehouseName,
            String skuCode,
            String skuName,
            String unit,
            BigDecimal quantity,
            BigDecimal unitCost,
            String idempotencyKey) {
    }

    /**
     * 可用量探测。
     *
     * <p><b>返回 {@code null} 表示「库存能力未启用」</b>，必须与「可用量为 0」严格区分 ——
     * 与 W3 的 {@code UNPRICED ≠ 0 元}、W4 的 {@code ordered_total_amount 可空} 同一语义纪律。
     * 调用方在 {@code null} 时不得把可用量当作 0。
     */
    record Availability(BigDecimal available, BigDecimal reserved) {
    }

    /** W6 调用点（W5 零调用）。 */
    void postInbound(InboundFact fact);

    /** W6 调用点（W5 零调用）；{@code null} = 库存能力未启用。 */
    Availability queryAvailability(Long skuId, Long warehouseId);
}
