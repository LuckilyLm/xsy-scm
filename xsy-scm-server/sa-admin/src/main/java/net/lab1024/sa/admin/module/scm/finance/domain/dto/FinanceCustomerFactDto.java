package net.lab1024.sa.admin.module.scm.finance.domain.dto;

import lombok.Data;

/**
 * 收付款共用的**客户事实**，由 {@code FinanceCounterpartySourceDao} 只读取得。
 *
 * <p>{@code customerNameSnapshot} 在收款事实生成时冻结（历史账不受主档改名影响），
 * {@code sellerId} 只用于数据范围判定，不落进 {@code finance_receipt}
 * （范围归属一律读时 join 取活值，第三批 Q23 / D-5）。
 */
@Data
public class FinanceCustomerFactDto {

    private Long customerId;

    /**
     * {@code customer.name}，非空白由 V8 的库级约束与列定义共同保证。
     */
    private String customerName;

    /**
     * 客户归属业务员；{@code customer.seller_id} 允许为空（尚未分配），
     * 空值只有全部范围可通过 —— 这是 P0 既有的失败关闭语义，不是本阶段新增的规则。
     */
    private Long sellerId;
}
