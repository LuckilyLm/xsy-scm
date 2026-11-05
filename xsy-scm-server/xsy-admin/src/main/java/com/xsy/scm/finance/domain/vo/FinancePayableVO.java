package com.xsy.scm.finance.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应付记录及其按查询计算的余额数据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FinancePayableVO extends FinanceLedgerVO {

    private Long payableId;

    private String payableNo;

    private Long purchaseOrderId;

    private String purchaseOrderNo;

    private Long supplierId;

    private String supplierName;

    private Long originalPayableId;

    private String originalPayableNo;
}
