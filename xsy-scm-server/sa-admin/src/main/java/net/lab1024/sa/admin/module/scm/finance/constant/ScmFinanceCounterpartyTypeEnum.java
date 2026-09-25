package net.lab1024.sa.admin.module.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 付款对方类型：付给供应商（应付 / 预付）或付给客户（退款）。
 *
 * <p>两侧的**数据范围口径不同**（D-5）：{@code SUPPLIER} 侧本期不收窄 ——
 * 供应商主档无 owner 列，按采购团队共享读取（P0 裁决 7），本期不为此新增 supplier 范围维度；
 * {@code CUSTOMER} 侧与收款同口径，按 {@code customer.seller_id} 用 customerSellerScope 收窄。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceCounterpartyTypeEnum {

    /**
     * 供应商：对应 {@code finance_payable} 的核销与预付。
     */
    SUPPLIER("供应商"),

    /**
     * 客户：对应退款付款（{@code source_type = ORDER_REFUND}）。
     */
    CUSTOMER("客户");

    private final String desc;
}
