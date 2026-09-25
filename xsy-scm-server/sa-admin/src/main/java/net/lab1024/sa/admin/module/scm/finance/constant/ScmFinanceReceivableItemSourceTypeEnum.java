package net.lab1024.sa.admin.module.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 应收明细的来源类型：正常明细锚定出库行，红字明细锚定退货行。
 *
 * <p><b>正常明细的锚点是 {@code inventory_outbound_item.id}，不是 {@code sales_order_item_id}</b>：
 * V63 刻意不给出库行建 {@code (sales_order_item_id)} 唯一索引（一条订单行将来可能被再出一行），
 * 所以行级唯一只能挂在出库行主键上，由 {@code uk_finance_receivable_item_source_active} 承担。
 *
 * <p>红字明细**不存**行级原明细指针：一条订单行可能对应多条出库行，不存在唯一的「原正常明细行」，
 * 假设 1:1 会造出一个指错行的引用（设计稿 §3.2）。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceReceivableItemSourceTypeEnum {

    /**
     * 正常明细：{@code source_id} = {@code inventory_outbound_item.id}。
     */
    INVENTORY_OUTBOUND_ITEM("库存出库明细"),

    /**
     * 红字明细：{@code source_id} = {@code order_return_item.id}。
     */
    ORDER_RETURN_ITEM("销售退货明细");

    private final String desc;
}
