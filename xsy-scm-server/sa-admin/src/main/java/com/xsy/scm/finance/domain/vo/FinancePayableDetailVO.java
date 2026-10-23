package com.xsy.scm.finance.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 应付单头、明细、关联红字、核销与操作日志。
 */
@Data
public class FinancePayableDetailVO {

    private FinancePayableVO payable;

    private List<FinancePayableItemVO> items;

    private List<FinancePayableVO> redEntries;

    private FinancePayableVO originalPayable;

    private List<FinanceWriteOffVO> writeOffs;

    private List<FinanceOperationLogVO> operationLogs;
}
