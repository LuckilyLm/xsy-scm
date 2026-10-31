package com.xsy.scm.finance.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应收记录及其按查询计算的余额数据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceReceivableVO extends FinanceLedgerVO {

    private Long receivableId;

    private String receivableNo;

    private Long orderId;

    private String orderNo;

    private Long customerId;

    private String customerName;

    private Long settlementCustomerId;

    private String settlementCustomerName;

    private Long originalReceivableId;

    private String originalReceivableNo;
}
