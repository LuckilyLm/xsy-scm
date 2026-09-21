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

    /**
     * 采购收货行：{@code source_document_item_id = purchase_receipt_item.id}。
     */
    PURCHASE_RECEIPT_ITEM("采购收货行"),

    /**
     * 出库单行：{@code source_document_item_id = inventory_outbound_item.id}。
     */
    SALES_OUTBOUND_ITEM("出库单行"),

    /**
     * 销售订单行（预留的来源）：{@code source_document_item_id = sales_order_item.id}。
     */
    SALES_ORDER_ITEM("销售订单行"),

    /**
     * 盘点单行：{@code source_document_item_id = inventory_stocktake_item.id}。
     *
     * <p>一条盘点明细行最多产生**一条**流水：{@code delta = 0} 的行不写流水
     * （数量恒为正，写不出「零差异」的流水），因此
     * {@code uk_inventory_movement_source_active} 的一行一流水语义在这里依然成立。
     */
    STOCKTAKE_ITEM("盘点单行"),

    /**
     * 报损报溢单行：{@code source_document_item_id = inventory_loss_gain_item.id}。
     *
     * <p>报损与报溢**共用一个来源类型**（方向由流水的 {@code movement_type} 表达）：
     * 它们出自同一张单据表的同一种行，拆成两个来源类型只会让查询多一次分支，
     * 而「这张单据是报损还是报溢」在单据头上已经能读到。
     */
    LOSS_GAIN_ITEM("报损报溢单行"),

    /**
     * 调拨**转出**行的来源类型：{@code source_document_item_id = inventory_transfer_item.id}。
     *
     * <p><b>为什么调拨要拆成两个来源类型</b>：这是全仓唯一「一条来源行会产生两条流水」的情况
     * （发出写转出、收货写转入）。而防重锚点是部分唯一索引
     * {@code uk_inventory_movement_source_active (source_document_type, source_document_item_id)}
     * —— 两条流水引用的是**同一个明细行 id**，若共用同一个来源类型，第二条插入必然冲突，
     * 收货就永远做不成。
     *
     * <p>该索引是 V19 冻结的 Q7/Q11 契约，不能为了调拨去放宽它。因此改用
     * 「来源类型本身编码方向」：转出与转入各占一个来源类型，各自在自己的
     * {@code (type, itemId)} 空间里唯一。副作用是正向的 ——
     * 可以直接按来源类型查出「所有转出流水」或「所有转入流水」。
     */
    TRANSFER_OUT_ITEM("调拨单行（转出）"),

    /**
     * 调拨**转入**行的来源类型；与 {@link #TRANSFER_OUT_ITEM} 分开以满足源身份唯一索引。
     */
    TRANSFER_IN_ITEM("调拨单行（转入）"),

    /**
     * 规格转换**转出**行的来源类型：{@code source_document_item_id = inventory_conversion_item.id}。
     *
     * <p>与调拨**同一个原因**拆成两个来源类型：同一条明细行会产生两条流水
     * （转出写源 SKU、转入写目标 SKU），而防重锚点是部分唯一索引
     * {@code uk_inventory_movement_source_active (source_document_type, source_document_item_id)}
     * —— 两条流水引用同一个明细行 id，共用一个来源类型第二条就插不进去。
     * 该索引是 V19 冻结的 Q7/Q11 契约，不为新能力放宽。
     */
    CONVERT_OUT_ITEM("转换单行（转出）"),

    /**
     * 规格转换**转入**行的来源类型；与 {@link #CONVERT_OUT_ITEM} 分开以满足源身份唯一索引。
     */
    CONVERT_IN_ITEM("转换单行（转入）");

    private final String desc;

    /**
     * 该值是否允许写入 {@code inventory_movement.source_document_type}。
     */
    public static boolean isSupported(String value) {
        for (ScmInventorySourceDocumentTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
