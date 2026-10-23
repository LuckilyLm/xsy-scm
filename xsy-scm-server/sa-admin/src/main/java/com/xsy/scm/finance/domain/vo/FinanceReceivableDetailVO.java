package com.xsy.scm.finance.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 应收单头、明细、关联红字、核销与操作日志。
 */
@Data
public class FinanceReceivableDetailVO {

    private FinanceReceivableVO receivable;

    private List<FinanceReceivableItemVO> items;

    private List<FinanceReceivableVO> redEntries;

    private FinanceReceivableVO originalReceivable;

    private List<FinanceWriteOffVO> writeOffs;

    private List<FinanceOperationLogVO> operationLogs;
}
