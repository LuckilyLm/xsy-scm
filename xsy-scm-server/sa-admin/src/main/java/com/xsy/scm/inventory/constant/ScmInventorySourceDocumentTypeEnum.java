package com.xsy.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 标识每条库存流水对应的来源单据。 来源类型与明细 ID 共同组成防重键；库存模块不能反向依赖采购模块的常量。
 */
@Getter
@RequiredArgsConstructor
public enum ScmInventorySourceDocumentTypeEnum {

    /**
     * 采购收货行：{@code source_document_item_id = xsy_purchase_receipt_item.id}。
     */
    PURCHASE_RECEIPT_ITEM("采购收货行"),

    /**
     * 出库单行：{@code source_document_item_id = xsy_inventory_outbound_item.id}。
     */
    SALES_OUTBOUND_ITEM("出库单行"),

    SALES_RETURN_RECEIPT_ITEM("销售退货接收行"),

    /**
     * 销售订单行（预留的来源）：{@code source_document_item_id = xsy_sales_order_item.id}。
     */
    SALES_ORDER_ITEM("销售订单行"),

    /**
     * 盘点单行：{@code source_document_item_id = xsy_inventory_stocktake_item.id}。
     *
     * <p>
     * 一条盘点明细行最多产生<b>一条</b>流水：{@code delta = 0} 的行不写流水 （数量恒为正，写不出「零差异」的流水），因此
     * {@code uk_inventory_movement_source_active} 的一行一流水语义在这里依然成立。
     */
    STOCKTAKE_ITEM("盘点单行"),

    /**
     * 报损报溢单行：{@code source_document_item_id = xsy_inventory_loss_gain_item.id}。
     *
     * <p>
     * 报损与报溢<b>共用一个来源类型</b>（方向由流水的 {@code movement_type}
     * 表达）：它们出自同一张单据表的同一种行，拆成两个来源类型只会让查询多一次分支，而「这张单据是报损还是报溢」在单据头上已经能读到。
     */
    LOSS_GAIN_ITEM("报损报溢单行"),

    /** 调拨发出和收货使用不同的流水来源类型，因为两条流水指向同一个调拨明细 ID。来源类型与明细 ID 共同防重，不能放宽唯一索引。 */
    TRANSFER_OUT_ITEM("调拨单行（转出）"),

    /**
     * 调拨<b>转入</b>行的来源类型；与 {@link #TRANSFER_OUT_ITEM} 分开以满足源身份唯一索引。
     */
    TRANSFER_IN_ITEM("调拨单行（转入）"),

    /** 规格转换的转出和转入共用同一明细 ID，但使用不同来源类型以满足流水来源唯一索引。不能为了转换放宽来源防重。 */
    CONVERT_OUT_ITEM("转换单行（转出）"),

    /**
     * 规格转换<b>转入</b>行的来源类型；与 {@link #CONVERT_OUT_ITEM} 分开以满足源身份唯一索引。
     */
    CONVERT_IN_ITEM("转换单行（转入）"),

    /** 配送线路只标识生成出库单的业务来源，库存流水仍以出库明细为唯一来源，避免同一流水出现两套防重依据。 */
    DELIVERY_ROUTE("配送线路"),

    /** 促销赠品以赠品权益 ID 作为库存流水来源，不挂销售订单行。一个权益最多产生一条有效出库流水，防重规则与其他库存来源一致。 */
    ORDER_PROMOTION_GIFT("促销赠品权益");

    private final String desc;

    /**
     * 该值是否允许写入 {@code xsy_inventory_movement.source_document_type}。
     */
    public static boolean isSupported(String sourceDocumentType) {
        for (ScmInventorySourceDocumentTypeEnum item : values()) {
            if (item.name().equals(sourceDocumentType)) {
                return true;
            }
        }
        return false;
    }
}
