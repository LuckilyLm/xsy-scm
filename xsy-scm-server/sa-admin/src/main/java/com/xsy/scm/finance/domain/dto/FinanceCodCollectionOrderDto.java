package com.xsy.scm.finance.domain.dto;

import lombok.Data;

/**
 * 货到付款收款需要的订单事实。
 *
 * <p>
 * <b>为什么 {@code codSettlement} 由 SQL 算</b>：财务域不允许依赖其它业务域（见 {@code ScmArchitectureTest}）， 因此不能引用订单域 /
 * 商城域的结算方式枚举；结算方式这个取值域只存在于订单表里，就地在 SQL 里判定， 财务侧只消费结论。
 */
@Data
public class FinanceCodCollectionOrderDto {

    /** 销售订单主键。 */
    private Long orderId;

    /** 下单客户主键（收款的结算对象由客户域规则解析，这里只给客户）。 */
    private Long customerId;

    /** 该订单的结算约定是否为货到付款。 */
    private Boolean codSettlement;
}
