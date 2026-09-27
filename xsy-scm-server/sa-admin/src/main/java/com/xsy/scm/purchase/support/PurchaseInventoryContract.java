package com.xsy.scm.purchase.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Compile-time boundary between purchase confirmation and inventory posting.
 *
 * <p>Purchase sends confirmed receipt facts through this interface; the inventory module supplies the implementation.
 * The caller and implementation share one transaction so inventory failure rolls back receipt confirmation.
 */
public interface PurchaseInventoryContract {

    /**
     * Stable inventory source type used with a receipt item id for duplicate protection.
     */
    String SOURCE_DOCUMENT_TYPE = "PURCHASE_RECEIPT_ITEM";

    /**
     * Inventory fact represented by one confirmed purchase receipt item.
     *
     * @param purchaseOrderId purchase order identifier
     * @param receiptId receipt identifier
     * @param receiptItemId stable source item identifier
     * @param warehouseId warehouse identifier inherited from the purchase order
     * @param skuId product SKU identifier
     * @param warehouseCode warehouse code snapshot
     * @param warehouseName warehouse name snapshot
     * @param skuCode SKU code snapshot
     * @param skuName SKU name snapshot
     * @param unit purchase unit snapshot
     * @param quantity effective received quantity
     * @param unitCost purchase price snapshot
     * @param idempotencyKey stable duplicate-protection key
     * @param occurredAt persisted receipt confirmation time
     * @param operator persisted receipt operator
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
            String idempotencyKey,
            OffsetDateTime occurredAt,
            String operator) {
    }

    /**
     * Available and reserved quantities returned by inventory.
     * A null value means the inventory integration is unavailable; zero means the balance is empty.
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
