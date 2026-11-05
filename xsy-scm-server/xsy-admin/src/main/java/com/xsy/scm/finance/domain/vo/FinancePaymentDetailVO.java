package com.xsy.scm.finance.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 付款单、分配、反向关联与操作日志。
 */
@Data
public class FinancePaymentDetailVO {

    private FinancePaymentQueryVO payment;

    private FinancePaymentQueryVO original;

    private FinancePaymentQueryVO reversal;

    private List<FinanceWriteOffVO> writeOffs;

    private List<FinanceOperationLogVO> operationLogs;
}
