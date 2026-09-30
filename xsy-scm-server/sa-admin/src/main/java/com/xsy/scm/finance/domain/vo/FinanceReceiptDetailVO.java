package com.xsy.scm.finance.domain.vo;

import lombok.Data;

import java.util.List;

/** Receipt row, allocations, reversal link and audit history. */
@Data
public class FinanceReceiptDetailVO {

    private FinanceReceiptQueryVO receipt;

    private FinanceReceiptQueryVO original;

    private FinanceReceiptQueryVO reversal;

    private List<FinanceWriteOffVO> writeOffs;

    private List<FinanceOperationLogVO> operationLogs;
}
