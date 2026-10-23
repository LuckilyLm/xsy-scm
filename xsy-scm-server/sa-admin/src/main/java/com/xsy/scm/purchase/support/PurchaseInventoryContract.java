package com.xsy.scm.purchase.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 采购确认通过本接口把已保存的收货事实交给库存模块。采购不直接更新库存余额，库存模块统一校验单位、锁定余额和生成流水。
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
     * 数量是<b>有效收货量</b>、单位与成本都是采购侧的<b>快照</b>；{@code idempotencyKey} 是稳定防重键， {@code occurredAt} / {@code operator}
     * 取<b>持久化的收货确认时刻与确认人</b>，不是写入时刻与当前登录人。
     */
    record InboundFact(Long purchaseOrderId, Long receiptId, Long receiptItemId, Long warehouseId, Long skuId,
        String warehouseCode, String warehouseName, String skuCode, String skuName, String unit, BigDecimal quantity,
        BigDecimal unitCost, String idempotencyKey, OffsetDateTime occurredAt, String operator,
        List<LotQuantity> lots) {
    }

    /**
     * 入库行分摊到某个批次的数量。
     *
     * <p>
     * 只带批次身份与数量：批次档案归追溯域，库存不反向依赖追溯域的类型。未启用批次追踪的仓库传空列表。
     */
    record LotQuantity(Long lotId, String lotNo, BigDecimal quantity) {
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
