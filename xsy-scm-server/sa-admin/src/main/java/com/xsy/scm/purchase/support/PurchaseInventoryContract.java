package com.xsy.scm.purchase.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 采购确认通过本接口把已保存的收货记录交给库存模块。采购不直接更新库存余额，库存模块统一校验单位、锁定余额和生成流水。
 */
public interface PurchaseInventoryContract {

    /**
     * 与收货明细 ID 一起使用的稳定库存来源类型，用于防重。
     */
    String SOURCE_DOCUMENT_TYPE = "PURCHASE_RECEIPT_ITEM";

    /**
     * 一张已确认收货行对应的入库数据（采购域给库存域的中立接口）。
     *
     * <p>
     * 数量是有效收货量，单位与成本都是采购侧当时的记录；{@code idempotencyKey} 是稳定防重键， {@code occurredAt} / {@code operator}
     * 取持久化的收货确认时刻与确认人，不是写入时刻与当前登录人。
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
     * 库存返回的可用量与预留量。null 表示库存集成不可用，0 表示余额为空。
     */
    record Availability(BigDecimal available, BigDecimal reserved) {
    }

    /**
     * 在调用方事务内提交收货数据；失败必须抛到收货确认。
     */
    void postInbound(InboundFact fact);

    /**
     * 读取可用量；null 表示集成不可用，余额为空时返回 0。
     */
    Availability queryAvailability(Long skuId, Long warehouseId);
}
