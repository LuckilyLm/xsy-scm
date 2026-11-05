package com.xsy.scm.finance.domain.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 独立退货的客户级红字来源：没有原订单，客户与结算主体都取客户档案。
 */
@Data
public class FinanceIndependentReturnSourceDto {

    private Long orderReturnId;

    private String returnNo;

    private Long customerId;

    private String customerNameSnapshot;

    /**
     * 结算主体：客户档案的 {@code settlement_customer_id}，共享结算时可能不是客户自己。
     */
    private Long settlementCustomerId;

    private String settlementCustomerNameSnapshot;

    /**
     * 红字的事件时点 = {@code xsy_order_return.approved_at}。
     */
    private OffsetDateTime approvedAt;

    private String approvedBy;

    private String reason;
}
