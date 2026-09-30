package com.xsy.scm.finance.domain.vo;

import lombok.Data;

import java.util.List;

/** Payment row, allocations, reversal link and audit history. */
@Data
public class FinancePaymentDetailVO {

    private FinancePaymentQueryVO payment;

    private FinancePaymentQueryVO original;

    private FinancePaymentQueryVO reversal;

    private List<FinanceWriteOffVO> writeOffs;

    private List<FinanceOperationLogVO> operationLogs;
}
