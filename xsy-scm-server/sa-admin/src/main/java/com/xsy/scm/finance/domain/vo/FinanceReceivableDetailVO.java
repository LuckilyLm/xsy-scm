package com.xsy.scm.finance.domain.vo;

import lombok.Data;

import java.util.List;

/** AR header, source lines, linked RED facts, write-offs and audit history. */
@Data
public class FinanceReceivableDetailVO {

    private FinanceReceivableVO receivable;

    private List<FinanceReceivableItemVO> items;

    private List<FinanceReceivableVO> redEntries;

    private FinanceReceivableVO originalReceivable;

    private List<FinanceWriteOffVO> writeOffs;

    private List<FinanceOperationLogVO> operationLogs;
}
