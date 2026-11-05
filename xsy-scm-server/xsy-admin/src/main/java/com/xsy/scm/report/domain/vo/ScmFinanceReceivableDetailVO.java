package com.xsy.scm.report.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 一笔正常应收及其关联的红字记录，以及所选截止日的余额。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmFinanceReceivableDetailVO extends ScmFinanceLedgerDetailVO {

    private Long receivableId;

    private String receivableNo;

    private String orderNo;

    private String customerName;
}
