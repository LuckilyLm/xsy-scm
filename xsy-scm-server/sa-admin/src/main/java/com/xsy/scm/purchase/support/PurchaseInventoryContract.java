package com.xsy.scm.purchase.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Compile-time boundary between purchase confirmation and inventory posting.
 *
 * <p>
 * Purchase sends confirmed receipt facts through this interface; the inventory module supplies the implementation. The
 * caller and implementation share one transaction so inventory failure rolls back receipt confirmation.
 */
public interface PurchaseInventoryContract {

    /**
     * Stable inventory source type used with a receipt item id for duplicate protection.
     */
    String SOURCE_DOCUMENT_TYPE = "PURCHASE_RECEIPT_ITEM";

    /**
     * 一张已确认收货行所代表的库存事实（采购域对库存域的中立契约）。
     *
     * <p>
     * 数量是**有效收货量**、单位与成本都是采购侧的**快照**；{@code idempotencyKey} 是稳定防重键， {@code occurredAt} / {@code operator}
     * 取**持久化的收货确认时刻与确认人**，不是写入时刻与当前登录人。
     */
    record InboundFact(Long purchaseOrderId, Long receiptId, Long receiptItemId, Long warehouseId, Long skuId,
            String warehouseCode, String warehouseName, String skuCode, String skuName, String unit,
            BigDecimal quantity, BigDecimal unitCost, String idempotencyKey, OffsetDateTime occurredAt,
            String operator) {
    }

    /**
     * Available and reserved quantities returned by inventory. A null value means the inventory integration is
     * unavailable; zero means the balance is empty.
     */
    record Availability(BigDecimal available, BigDecimal reserved) {
    }

    /**
     * Posts the receipt fact inside the caller's transaction; failures must propagate to receipt confirmation.
     */
    void postInbound(InboundFact fact);

    /**
     * Reads availability; null denotes an unavailable integration, while an empty balance is returned as zero.
     */
    Availability queryAvailability(Long skuId, Long warehouseId);
}
