package com.xsy.scm.finance.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 收款单、分配、反向关联与操作日志。
 */
@Data
public class FinanceReceiptDetailVO {

    private FinanceReceiptQueryVO receipt;

    private FinanceReceiptQueryVO original;

    private FinanceReceiptQueryVO reversal;

    private List<FinanceWriteOffVO> writeOffs;

    private List<FinanceOperationLogVO> operationLogs;
}
