package net.lab1024.sa.admin.module.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 库存流水的来源单据类型（W6 Target Design §2.2 / §4.1）。
 *
 * <p>该值持久化到 {@code inventory_movement.source_document_type}，并与
 * {@code source_document_item_id} 一起构成**稳定唯一源键**（W5 TD §8.5 冻结的口径），
 * 由部分唯一索引 {@code uk_inventory_movement_source_active} 在 DB 层兜底防重。
 *
 * <p><b>为什么不直接引用 {@code PurchaseInventoryContract.SOURCE_DOCUMENT_TYPE}</b>：
 * 库存域是**与来源无关**的领域原语（未来销售出库 / 调拨 / 盘点都会写入同一张流水表），
 * 把 purchase 的契约常量嵌进库存域常量会让库存域被迫依赖某一个来源域。
 * 两个常量的一致性由单测 {@code ScmInventorySourceDocumentTypeEnumTest} 强制
 * —— 值相等是**断言**，不是靠人记得。
 */
@Getter
@RequiredArgsConstructor
public enum ScmInventorySourceDocumentTypeEnum {

    /** 采购收货行：{@code source_document_item_id = purchase_receipt_item.id}。 */
    PURCHASE_RECEIPT_ITEM("采购收货行"),

    /** 出库单行：{@code source_document_item_id = inventory_outbound_item.id}。 */
    SALES_OUTBOUND_ITEM("出库单行"),

    /** 销售订单行（预留的来源）：{@code source_document_item_id = sales_order_item.id}。 */
    SALES_ORDER_ITEM("销售订单行");

    private final String desc;

    /** 该值是否允许写入 {@code inventory_movement.source_document_type}。 */
    public static boolean isSupported(String value) {
        for (ScmInventorySourceDocumentTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
