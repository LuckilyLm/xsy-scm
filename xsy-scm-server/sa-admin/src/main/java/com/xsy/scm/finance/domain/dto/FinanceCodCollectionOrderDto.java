package com.xsy.scm.finance.domain.dto;

import lombok.Data;

/**
 * 货到付款收款需要的订单数据。
 *
 * <p>
 * {@code codSettlement} 由 SQL 计算：财务域不允许依赖其它业务域（见 {@code ScmArchitectureTest}），不能引用订单域或商城域的结算方式枚举； 结算方式只存在于订单表里，就地在 SQL
 * 里判断，财务侧只使用结论。
 */
@Data
public class FinanceCodCollectionOrderDto {

    /**
     * 销售订单主键。
     */
    private Long orderId;

    private String orderNo;

    private Long sellerId;

    /**
     * 下单客户主键用于当前数据范围校验；收款结算主体取自历史应收记录。
     */
    private Long customerId;

    /**
     * 该订单的结算约定是否为货到付款。
     */
    private Boolean codSettlement;
}
