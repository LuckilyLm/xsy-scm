package com.xsy.scm.report.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 一笔正常应付及其关联的红字记录，以及所选截止日的余额。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmFinancePayableDetailVO extends ScmFinanceLedgerDetailVO {

    private Long payableId;

    private String payableNo;

    private String purchaseOrderNo;

    private String supplierName;
}
